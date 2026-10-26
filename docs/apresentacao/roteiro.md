# Roteiro da apresentação — 20 min

> **Esqueleto.** Fechamento por Paulo. Cronometrar no ensaio de **02/11**; gravar em **03/11**.
> Divisão dos blocos e os nove passos da demo em
> [divisao-de-trabalho.md](../divisao-de-trabalho.md#divisão-da-apresentação--20-min).

| Bloco | Tempo | Quem | Acumulado |
|---|---|---|---|
| 1. Problema e justificativa | 2:00 | Paulo | 2:00 |
| 2. C4 | 3:00 | Paulo | 5:00 |
| 3. Padrões | 3:00 | Guilherme · Vinicius · Hugo | 8:00 |
| 4. Segurança | 1:30 | Rafael | 9:30 |
| 5. Observabilidade e implantação | 1:30 | Sesaque | 11:00 |
| 6. Demo | 8:00 | Marcus conduz | 19:00 |
| 7. O que não fizemos e por quê | 1:00 | Paulo | **20:00** |

## Regras do ensaio

- Cada bloco tem **um** slide-âncora. Passar de dois é sinal de que o bloco está longo
- Todo padrão citado vem com **por que existe** e **quando não vale** — é a régua da disciplina
- A demo roda de `scripts/demo.sh`. Nada é digitado ao vivo
- Estourou o tempo no ensaio? Corta de dentro do bloco 3, nunca do bloco 6

## Notas de fala

### Bloco 3 · Eventos e Observer — Hugo · 1:00

**Slide-âncora:** a tabela "O Observer mapeado" da
[ADR-006](../adr/ADR-006-kafka-como-barramento.md#o-observer-mapeado), com o tópico
`risco.elegibilidade.v1` no centro e os dois consumer groups saindo dele.

| Tempo | Fala |
|---|---|
| 0:00 | "Entre os serviços de domínio não existe chamada HTTP: só eventos no Kafka. É o Observer: o tópico é o *Subject*, e cada consumer group é um observador que se inscreveu." |
| 0:15 | "O risco publica o parecer uma vez e não sabe quem lê. Hoje leem dois: a projeção do crédito e o antifraude, que é Python. O antifraude entrou sem mudar uma linha do risco." |
| 0:30 | "A chave de todo evento é o `solicitacaoId`: tudo de uma solicitação cai na mesma partição, nos três tópicos, seja Java ou Python publicando. É a única ordem que o domínio exige. O custo é a consistência eventual: a proposta aparece antes da análise antifraude, e o BFF mostra o que já chegou." |
| 0:50 | "Quando não vale: quando quem pergunta precisa da resposta na hora. Por isso a consulta do BFF ao antifraude é HTTP." |

### Demo · passo 3 — fluxo por evento · 1:30

O script abre a solicitação pelo BFF e lista, em ordem, os eventos com aquela chave: crédito,
risco e antifraude, todos na mesma partição. Falar enquanto a lista aparece: "nenhum serviço
chamou outro, cada um só publicou". Depois, no Kafka UI, abrir uma mensagem do
`antifraude.analise.v1` e mostrar os headers `ce_*` iguais aos do evento Java logo acima, e o
`traceparent` que leva o trace adiante. Fechar no bloco antifraude do `/v2/propostas`, com o
score e o motivo.

<!-- Cada dono preenche a sua, até 01/11. -->

### Bloco 3 · Clean/Hexagonal — Guilherme · 1:00

**Slide-âncora:** à esquerda, o hexágono do risco (diagrama do
[README do serviço](../../servico-risco/README.md#o-hexágono)); à direita, a tabela de
equivalência da [ADR-004](../adr/ADR-004-clean-architecture-e-hexagonal.md#a-equivalência), só
com as linhas Entities, Use Cases, Boundaries e Adapters.

| Tempo | Fala |
|---|---|
| 0:00 | "Clean e Hexagonal são a mesma regra com nomes diferentes: toda dependência aponta para dentro. O crédito usa um vocabulário, o risco usa o outro, e a tabela mostra que é a mesma coisa." |
| 0:15 | "No risco, o centro é o agregado `AnaliseDeCredito`, com a regra de elegibilidade. Ele não importa Spring, nem Kafka, nem SQL. Os três consumidores Kafka e o controller são adaptadores de entrada; o JDBC e o publicador do `risco.elegibilidade.v1`, de saída." |
| 0:35 | "Quem garante não é a boa vontade: é um teste de arquitetura que quebra o build se o domínio importar um framework. E o domínio roda sozinho — 19 casos em 34 milissegundos, sem broker e sem banco." |
| 0:50 | "Quando não vale: serviço sem regra. Por isso o BFF não é hexagonal — lá, porta e adaptador seriam camada decorativa." |

### Bloco 5 · Observabilidade e implantação — Sesaque · 1:10

**Slide-âncora:** à esquerda, o caminho dos sinais da
[ADR-010](../adr/ADR-010-opentelemetry-e-loki.md#decisão) — 5 serviços → Collector → Tempo,
Prometheus e Loki → Grafana; à direita, a tabela de recursos de `infra/k8s/` da
[ADR-011](../adr/ADR-011-docker-kubernetes-ci-cd.md#kubernetes). Embaixo, o grafo do CI do Guilherme.

| Tempo | Fala |
|---|---|
| 0:00 | "Uma solicitação passa por quatro processos, dois runtimes e três tópicos. Sem correlação, cada serviço só vê o próprio pedaço. Por isso os cinco falam OpenTelemetry: os Java com o agente na imagem, sem uma linha de código; os Python com o `opentelemetry-instrument`." |
| 0:15 | "Tudo vai para um Collector só, que manda traces para o Tempo, métricas para o Prometheus e logs para o Loki. Nenhum serviço sabe qual backend existe: trocar Loki por ELK é mudar um arquivo." |
| 0:30 | "O ponto difícil é o Kafka. O `traceparent` precisa viajar no cabeçalho da mensagem: o agente Java faz isso sozinho, e no Python são dez linhas, com teste. O resultado é um trace único do BFF até o read model, atravessando três tópicos — está no passo 8 da demo." |
| 0:45 | "Loki e não ELK: o Loki indexa só os labels e roda com 65 MB; o Elasticsearch indexa cada palavra e pede um giga de heap. Quando não vale: busca full-text em log histórico, auditoria — aí o ELK ganha." |
| 0:55 | "Na implantação, o mesmo sistema sobe no Kubernetes: Postgres e Kafka em StatefulSet com volume persistente, cada serviço num Deployment com probes. Apagou um pod, o ReplicaSet recria em segundos — é o passo 9. Quando não vale: seis containers de demonstração; com nosso volume, o compose resolveria." |

### Demo · passo 8 — trace no Grafana · 1:30

O script acha no Tempo o trace da solicitação do passo 3 pela chave do Kafka e imprime quantos
spans cada serviço gerou. Abrir o link do Explore: a cascata começa no `POST /v1/solicitacoes` do
BFF, desce para o crédito e atravessa `credito.solicitacao`, `risco.elegibilidade` e
`antifraude.analise`. Falar: "um trace, quatro serviços, Java e Python, três saltos de fila".
Clicar no ícone de logs de um span do risco: abre no Loki só as linhas daquele trace. Fechar no
dashboard *01 · Latência do BFF* com o p95 por rota.

### Demo · passo 9 — self-healing · 0:30

Cluster kind já de pé (`scripts/k8s-local.sh`). O script apaga o pod do `servico-credito` e
mostra o `get pods -w`: o antigo vai para `Terminating` (aparece `Error`, que é só a JVM saindo com
143), o ReplicaSet cria outro e ele fica `1/1 Running` em poucos segundos. Falar: "ninguém mandou
recriar; o Deployment pede uma réplica e o Kubernetes corrige a diferença".

### Bloco 5 · CI/CD — Guilherme, dentro do bloco do Sesaque · 0:20

**No slide do Sesaque:** o grafo do GitHub Actions de uma execução na `main`.

"Todo PR roda Maven e pytest em paralelo — teste automatizado nas duas linguagens, cada uma com o
seu gerenciador de dependências — e constrói os seis Dockerfiles multi-stage. Na `main`, a mesma
imagem vai para o GHCR e sobe num cluster kind dentro do próprio runner." (B62, B64, B67 ·
[ADR-011](../adr/ADR-011-docker-kubernetes-ci-cd.md))

### Demo · passo 2 — o que o risco põe na tela

Com `ana.analista`, o `GET /v1/analises` devolve os pareceres reais que o risco acabou de
publicar — não uma lista fixa. Se der tempo no passo 6: com o risco parado, a mesma chamada vira
**503 com `"fonte": "risco"`**, e volta a 200 quando ele sobe.
