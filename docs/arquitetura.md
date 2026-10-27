# Arquitetura do sistema de concessão de crédito

> Consolidação por Paulo. As seções 1 a 4, 9 e 10 estão escritas; 5 a 8 ficam com os donos dos
> respectivos padrões, até 01/11. Cada seção puxa de uma ADR; as decisões completas, com
> alternativas descartadas e condição de falha, estão em [`adr/`](adr/).

## 1. O domínio e o problema

O sistema concede crédito pessoal. Uma solicitação entra pela borda e percorre quatro contextos
antes de virar dinheiro na conta do cliente:

```
solicitação → análise de risco → análise antifraude → proposta → contrato → liberação
```

Volume estimado: **~1.000 solicitações por dia**, com picos no início do mês. Não é um número
grande, e a arquitetura é desenhada sabendo disso — o que justifica a separação não é escala, é
que **as quatro etapas têm necessidades incompatíveis entre si**:

| Etapa | O que ela precisa | O que acontece se ela ficar junto das outras |
|---|---|---|
| **Antifraude** | CPU para calcular score; tolera demora, ninguém espera na tela | Um pico de análise consome o pool de threads e derruba a consulta de proposta |
| **Consulta de proposta** | Leitura rápida e alta disponibilidade | Fica refém da latência de quem escreve |
| **Liberação** | Consistência forte com o core bancário | Precisa de um caminho que não tolere perda, diferente do resto |
| **Crédito** | Proteger a invariante da proposta sob concorrência | Mistura regra de negócio com orquestração de terceiros |

A tensão central é entre **escrita consistente** e **leitura disponível**. A proposta precisa ser
aceita uma vez só, e isso é uma invariante transacional. Mas a tela do cliente precisa responder
mesmo quando o antifraude está fora do ar — porque a proposta, sem o bloco de fraude, ainda tem
valor para quem olha. Essas duas exigências não cabem no mesmo caminho de código, e é delas que
sai quase toda decisão deste documento.

## 2. Por que multiplataforma

### Contra o monolito

Quatro serviços de domínio e um BFF, cada um com seu processo, sua imagem e seu schema. Em vez
de um processo único com módulos internos.

**O ganho real é isolamento de falha e de carga.** O antifraude escala e cai sozinho; um pico
dele não aparece na consulta de proposta. Cada contexto libera no seu ritmo. Nenhum serviço de
domínio conhece o endereço de outro, o que mantém as fronteiras honestas ao longo do tempo.

**O custo é alto e vale dizer qual é.** Consistência eventual deixa de ser exceção e vira regra:
a proposta aparece na tela antes de o parecer de risco chegar, e toda leitura precisa lidar com
dado ainda não projetado. Falha parcial passa a ser o estado normal, o que obriga a borda a ter
circuit breaker, timeout e fallback. Depurar exige correlação por trace — sem isso, "a
solicitação sumiu" não tem resposta. E sobem cinco processos, um broker, um IdP e um banco antes
de qualquer tela aparecer.

**Quando um monolito modular resolveria melhor:** neste volume, resolveria. Com ~1.000
solicitações/dia, um processo com módulos bem separados e um banco com schemas entregaria a
mesma regra de negócio com uma fração do custo operacional. A separação se paga aqui pelo
isolamento do antifraude e pela liberação independente — não por escala, que não existe.
O raciocínio completo, com as alternativas descartadas, está na [ADR-001](adr/ADR-001-multiplataforma-em-vez-de-monolito.md).

### Dois runtimes

| Runtime | Serviços | Motivo |
|---|---|---|
| **Java 21 / Spring Boot** | `bff`, `servico-credito`, `servico-risco` | Crédito e risco já existiam, com teste e com CloudEvents. O BFF precisa de Resource Server e de resiliência maduros, e ambos são de primeira classe nesse ecossistema |
| **Python 3.12 / FastAPI** | `servico-antifraude`, `servico-contratacao` | Análise de fraude é regra estatística sobre dados, terreno de `pandas` e `scikit-learn`. O segundo serviço existe para provar que interoperar não foi acidente |

O contato entre os dois lados é o **evento**, nunca a linguagem: nenhum código, biblioteca ou
modelo é compartilhado. O preço disso é que o envelope CloudEvents é implementado duas vezes, e
divergência só aparece em runtime — por isso o teste de ida e volta Python ↔ Java é pré-requisito,
não detalhe. Detalhes e custos na [ADR-003](adr/ADR-003-dois-runtimes-java-e-python.md).

**Quando uma linguagem só seria melhor:** com um time que domina apenas uma, a segunda vira custo
de CI, de instrumentação e de plantão sem retorno. Poliglota se paga quando o ecossistema resolve
o problema, não quando demonstra que é possível.

## 3. Os três eixos

### Comunicação

**Síncrono só na borda.** `app-cliente → bff → serviços`. Entre serviços de domínio não existe
uma única chamada HTTP: tudo é evento no Kafka. Essa regra é o que impede a arquitetura de virar
um monolito distribuído — nenhum serviço de domínio tem o endereço de outro para chamar.

Seis tópicos, envelope **CloudEvents 1.0** em binary mode, três partições cada, chave
`solicitacaoId`:

| Tópico | Produtor | Consumidores |
|---|---|---|
| `credito.solicitacao.v1` | credito | risco |
| `risco.elegibilidade.v1` | risco | credito, antifraude |
| `antifraude.analise.v1` | antifraude | credito |
| `credito.proposta.v1` | credito | contratacao |
| `contratacao.contrato.v1` | contratacao | credito |
| `contratacao.liberacao.v1` | contratacao | credito |

A chave de partição é `solicitacaoId` porque a única ordem que o domínio exige é **por
solicitação** — duas solicitações diferentes podem ser processadas em qualquer ordem.

Dois tópicos têm fila de mensagens mortas declarada — `credito.solicitacao.v1.dlq` e
`risco.elegibilidade.v1.dlq` —, para onde vai a mensagem que falha depois das tentativas, com os
cabeçalhos da falha preservados para reprocessamento manual. Os outros quatro ainda não têm, e
hoje uma falha neles é reprocessada pelo offset. O contrato completo está em
[`contratos/asyncapi.yaml`](contratos/asyncapi.yaml).

### Dados

Database per Service: **um schema e um usuário por serviço**, com `GRANT` restrito ao próprio
schema e `REVOKE ALL ON SCHEMA public FROM PUBLIC`. Nenhum JOIN cruza fronteira, porque o banco
não deixa — um `SELECT` cross-schema falha com `permission denied`.

**O trade-off assumido:** são quatro schemas numa instância só de PostgreSQL, não quatro
instâncias. Quatro instâncias dariam isolamento de falha e de recurso de verdade; o isolamento
lógico entrega a fronteira de acesso, que é o que protege o modelo, mas não protege contra um
serviço consumir toda a conexão do banco. Para o volume deste sistema, o segundo risco é menor
que o custo das quatro instâncias. A decisão está na [ADR-002](adr/ADR-002-fronteiras-de-contexto-e-database-per-service.md).

### Implantação

Container por serviço, com `Dockerfile` multi-stage — o JDK e o Maven ficam no estágio de build e
não vão para a imagem final. Seis imagens: os cinco serviços e a SPA.

`docker compose up` sobe a infraestrutura; `--profile apps` acrescenta os serviços. Em Kubernetes,
`infra/k8s/` traz Namespace, seis Deployments, oito Services, dois ConfigMaps e um Secret, além
dos StatefulSets de Postgres e Kafka. Decisões em [ADR-011](adr/ADR-011-docker-kubernetes-ci-cd.md).

## 4. O desenho — C4

Três níveis, do mais externo ao mais interno. Os arquivos editáveis, os PNG e a explicação de
cada um estão em [`c4/`](c4/).

**[Nível 1 — Contexto](c4/README-n1-contexto.md).** Quem usa o sistema e com que sistemas de fora
ele fala. Dois atores — cliente e analista — e dois sistemas externos: o Keycloak, que emite as
identidades, e o core bancário, que libera o valor. Antifraude e risco **não** aparecem aqui:
são contextos internos, e tratá-los como terceiros seria descrever outro sistema.

**[Nível 2 — Containers](c4/README-n2-container.md).** As unidades que sobem separadas, com
runtime e porta. É o diagrama onde os dois primeiros eixos ficam visíveis: não há uma seta de
HTTP entre serviços de domínio, e cada serviço toca apenas o próprio schema.

**[Nível 3 — Componentes](c4/README-n3-credito.md).** O interior do serviço de crédito: as
camadas da Clean Architecture, o agregado que protege a invariante, a projeção CQRS e os
adaptadores. É o nível que mostra a regra de dependência apontando para dentro.

## 5. Padrões internos
<!-- Clean/Hexagonal e a equivalência · DDD tático · CQRS · Observer → Kafka.
     ADR-002, 004, 005, 006. -->

## 6. Comunicação, contratos e resiliência
<!-- Tópicos, consumer groups, consistência eventual · BFF e as 3 fontes ·
     circuit breaker, retry só em GET, timeout, bulkhead · versionamento. ADR-007, 008. -->

## 7. Segurança
<!-- IdP, OAuth2/OIDC, PKCE, client credentials, validação do JWT, 401 vs 403. ADR-009. -->

## 8. Observabilidade e operação
<!-- Três sinais, trace atravessando a fila, 5 dashboards, Docker, K8s, CI/CD. ADR-010, 011. -->

## 9. Antipadrões evitados

Cada um destes tem um mecanismo que o impede, não só uma intenção.

**Monolito distribuído.** Serviços separados que se chamam em cadeia síncrona têm o custo da
distribuição e nenhum dos ganhos: um fora do ar derruba todos. Aqui a única chamada HTTP parte da
borda; entre domínios só há evento. O mecanismo é a ausência de endereço — nenhum serviço de
domínio tem a URL de outro configurada.

**Fragmentação excessiva.** Contratação e Liberação são um serviço só, de propósito. As duas
etapas mudam sempre pelo mesmo motivo — regra de formalização — e separá-las criaria um serviço de
duas tabelas sem dono de regra próprio, com mais deploys e mais saltos de rede e nenhum
acoplamento a menos. Fronteira de serviço segue motivo de mudança, não substantivo do domínio.

**Camada decorativa.** Um BFF que repassa 1:1 é um salto de rede com nome bonito. Este agrega
três fontes, degrada parcialmente quando duas delas falham e mantém duas versões da API vivas ao
mesmo tempo. Se um dia a agregação cair para uma fonte, o certo é remover o BFF, não mantê-lo por
simetria.

**Retry em operação não idempotente.** Repetir um `POST` que cria solicitação gera duplicata. O
retry está configurado apenas nos `GET`; o `POST /v1/solicitacoes` fica de fora, com circuit
breaker e bulkhead, que não repetem nada. Há teste que falha se alguém acrescentar retry ali.

**Password grant.** A aplicação nunca vê a senha do usuário: *direct access grants* está
desligado no client público, e a senha só é digitada na tela do IdP. O fluxo é Authorization Code
com PKCE S256.

**Secret no cliente.** A SPA roda no navegador e não guarda segredo nenhum — qualquer um lê o
JavaScript. Por isso ela é um client público, e a prova de posse é o `code_verifier`, descartável
a cada login.

## 10. O que ficou de fora, e por quê

Escopo decidido, não lacuna. Cada linha é uma coisa que a arquitetura **poderia** ter e não tem,
com o critério que a traria de volta.

| Fora | Por que não | O que faria valer |
|---|---|---|
| **CQRS completo**, com dois processos e dois bancos | Dobra a superfície operacional para separar cargas que hoje crescem no mesmo ritmo | Leitura e escrita escalando em ritmos diferentes de verdade |
| **Quatro instâncias de PostgreSQL** | O isolamento por schema e usuário já entrega a fronteira de acesso | Um serviço consumindo conexão a ponto de afetar os outros |
| **Service mesh, Ingress, autoscaling** | Resolvem problemas de operação que este sistema ainda não tem | Mais de um cluster, ou política de tráfego entre serviços |
| **GitOps** | O pipeline já aplica os manifests; um operador reconciliando estado seria uma peça a mais para manter | Mais de um ambiente com deriva de configuração |
| **gRPC / Protobuf** | Nenhum par de serviços troca volume ou frequência que justifique o custo do schema binário | Caminho quente com latência medida e inaceitável em JSON |
| **Componente de RAG** | Não há pergunta de negócio hoje que precise de busca semântica sobre o histórico | Volume de consulta em linguagem natural sobre propostas antigas |
| **Zero trust entre serviços** | A validação completa acontece na borda; os serviços confiam na rede interna | Rede compartilhada com cargas de terceiros |

A régua é a mesma em todas: **um padrão que não resolve um problema que existe é custo**. A
primeira vez que um deles passar a resolver, a decisão volta para a mesa — cada ADR declara a
condição mensurável que a reabre.
