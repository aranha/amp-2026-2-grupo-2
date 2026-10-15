# Plano de trabalho — Arquitetura Multiplataforma

Grupo 2 · 7 integrantes · apresentação em **4 de novembro de 2026**.

Este documento define **o que vamos construir e por quê**. Quem faz cada parte está em
[divisao-de-trabalho.md](divisao-de-trabalho.md); o que precisa estar pronto para a entrega
valer os 100 pontos está em [checklist-entrega.md](checklist-entrega.md).

---

## 1. A régua

O trabalho vale os **100 pontos** da disciplina e é a única avaliação prevista. A expectativa é
escolher um domínio próprio e fazer com ele, camada por camada, o que o professor fez com o
PizzaExpress ao longo dos 5 encontros.

A régua que ele repete em toda aula:

> Para cada padrão, mostrar **por que existe**, **como foi aplicado** e **quando não vale a pena**.

Um padrão sem justificativa é o que ele chama de *inveja de microsserviço* ou *camada decorativa*.
Isso muda como escrevemos tudo: cada ADR termina com uma seção "quando não vale", e o último
bloco da apresentação é dedicado ao que **não** fizemos e por quê.

Entrega: apresentação de até 20 min (≈11 de teoria, ≈9 de demo), slides, vídeo, código e
diagramas postados no Canvas.

---

## 2. Domínio e recorte

**Domínio: concessão de crédito**, herdado do EventStorming feito na disciplina de Arquitetura
Reativa e Event-Driven. Reaproveitamos o modelo (eventos, políticas, fronteiras) e os dois
serviços Java já escritos; o trabalho novo é transformar aquilo numa arquitetura multiplataforma
completa.

As cinco fronteiras identificadas no EventStorming: **Crédito**, **Risco**, **Antifraude**,
**Contratação** e **Liberação**.

O enunciado pede no mínimo **3 a 4 serviços de domínio + 1 BFF, em pelo menos 2 linguagens, cada um
com seu schema ou banco**. Nosso recorte funde Contratação e Liberação num serviço só — as duas
etapas sempre mudam pelo mesmo motivo (regra de formalização) e separá-las criaria um serviço de
duas tabelas. Essa fusão é deliberada e entra na apresentação como exemplo de **fragmentação
excessiva evitada**.

---

## 3. Arquitetura alvo

### 3.1 Os componentes

| Componente | Runtime | Bounded context | Agregado(s) | Schema |
|---|---|---|---|---|
| `app-cliente` | SPA (JS puro + Vite) | — | — | — |
| `bff` | Java 21 / Spring Boot | borda | — | — |
| `servico-credito` | Java 21 / Spring Boot | Crédito | `SolicitacaoDeCredito`, `PropostaDeCredito` | `credito` |
| `servico-risco` | Java 21 / Spring Boot | Risco | `AnaliseDeCredito` | `risco` |
| `servico-antifraude` | Python 3.12 / FastAPI | Antifraude | `AnaliseAntifraude` | `antifraude` |
| `servico-contratacao` | Python 3.12 / FastAPI | Contratação + Liberação | `Contrato`, `LiberacaoDeCredito` | `contratacao` |

**Por que duas linguagens, e não uma.** Java/Spring nos serviços de Crédito e Risco porque já
existem, já têm teste e já falam CloudEvents — jogar fora seria desperdício. Python/FastAPI no
Antifraude porque a análise de fraude é regra estatística sobre dados, onde o ecossistema Python
(pandas, scikit-learn) é a escolha natural de mercado, e no Contratação para provar que a
interoperabilidade não é exceção. **Quando não vale:** se o time fosse só de Java, a segunda
linguagem viraria custo de CI, de observabilidade e de plantão sem retorno — poliglota se paga
quando o ecossistema da linguagem resolve o problema, não quando é só demonstração.

**Por que multiplataforma e não monolito.** As partes têm necessidades diferentes: o Antifraude é
CPU-bound e tolera latência; a consulta de proposta precisa de leitura rápida e alta
disponibilidade; a Liberação precisa de consistência forte com o core bancário. Num monolito, o
pico de antifraude degrada a consulta. **Quando não vale:** com ~1.000 solicitações/dia
(frequência estimada no diário de eventos), um monolito modular resolveria — e isso vai dito na
apresentação. Fazemos multiplataforma porque a disciplina pede e porque o eixo de implantação
independente é real para o time de Antifraude.

### 3.2 Os três eixos

**Comunicação.** Síncrona só na borda: `app-cliente → BFF → serviços`. Entre serviços de domínio,
**nenhuma chamada síncrona** — só eventos. É assim que evitamos o *distributed monolith*: nenhum
serviço de domínio conhece o endereço de outro.

**Dados.** Database per Service. Um Postgres, mas **um schema e um usuário por serviço**, com
`GRANT` restrito ao próprio schema. Nenhum JOIN cruza fronteira. **Trade-off assumido:** quatro
instâncias de Postgres seriam o ideal de produção; num trabalho acadêmico o custo não se paga, e o
isolamento lógico prova a mesma regra. Demonstrável: um `SELECT` cross-schema falha com
`permission denied` ao vivo.

**Implantação.** Container por serviço (Dockerfile multi-stage), `docker-compose` para a demo e
manifests Kubernetes para provar orquestração e self-healing.

### 3.3 Fluxo de eventos (Kafka)

Envelope **CloudEvents 1.0** em binary mode, herdado da disciplina anterior.

| Tópico | Produtor | Consumidores (consumer group) | Chave de partição |
|---|---|---|---|
| `credito.solicitacao.v1` | credito | risco | `solicitacaoId` |
| `risco.elegibilidade.v1` | risco | credito, antifraude | `solicitacaoId` |
| `antifraude.analise.v1` | antifraude | credito | `solicitacaoId` |
| `credito.proposta.v1` | credito | contratacao | `solicitacaoId` |
| `contratacao.contrato.v1` | contratacao | credito | `solicitacaoId` |
| `contratacao.liberacao.v1` | contratacao | credito | `solicitacaoId` |
| `<topico>.dlq` | consumidor que falhou | reprocessamento manual | — |

3 partições por tópico. Chave `solicitacaoId` garante ordem por solicitação — que é a única ordem
que o domínio exige. **Observer mapeado:** tópico = *Subject*, consumer group = *attach*,
adicionar um consumidor não toca o produtor.

### 3.4 CQRS

No `servico-credito`:

- **Command side** — `POST /v1/solicitacoes` entra pelo agregado `SolicitacaoDeCredito`, que
  protege a invariante (não aceitar duas vezes a mesma proposta → **409**). Escreve na tabela
  normalizada `credito.solicitacao`.
- **Query side** — tabela desnormalizada `credito.proposta_view`, populada por consumo de
  `risco.elegibilidade.v1`, `antifraude.analise.v1` e `contratacao.contrato.v1`.
  `GET /v1/propostas/{id}` lê só dela, sem JOIN e sem tocar o lado de escrita.

É CQRS com schemas separados sincronizados por evento, no mesmo processo. **Quando não vale:**
CQRS completo (dois processos, dois bancos) dobra a superfície operacional; só se paga quando as
cargas de leitura e escrita escalam em ritmos diferentes de verdade. Aqui elas não escalam.

### 3.5 BFF e resiliência

O BFF **não é proxy**. `GET /v2/propostas/{id}` agrega três fontes: a proposta (crédito), o
parecer de risco e o status antifraude. Se fosse repasse 1:1 seria camada decorativa, e diríamos.

Resilience4j, por downstream:

| Padrão | Configuração | Por quê |
|---|---|---|
| Timeout | 2s connect / 3s read | Nenhuma chamada pendura o BFF |
| Circuit Breaker | janela 10, 50% falha, abre 10s, half-open 3 | Para de bater em serviço caído |
| Retry | **só em GET**, 3 tentativas, backoff exponencial | GET é idempotente; retry em `POST /solicitacoes` criaria solicitação duplicada |
| Bulkhead | semáforo de 10 por downstream | Antifraude lento não consome o pool da consulta de proposta |
| Fallback | devolve o read model sem o bloco antifraude, com `antifraudeIndisponivel: true` | Degradação parcial em vez de 500 |

### 3.6 Versionamento de API

- `GET /v1/propostas/{id}` → campos planos: `valorAprovado`, `taxa`, `parcelas`
- `GET /v2/propostas/{id}` → objeto `condicoes { valorAprovado, taxa, parcelas, cet }`, sem `taxa`
  no topo

Remover `taxa` do topo é **breaking change** — daí a v2. As duas convivem no mesmo BFF; a v1
responde com header `Deprecation` e `Sunset`. Demonstrado ao vivo, lado a lado.

### 3.7 Segurança

Keycloak, realm `credito`.

| Client | Tipo | Fluxo |
|---|---|---|
| `app-cliente` | público | Authorization Code + **PKCE (S256)** |
| `bff` | confidencial | valida token, audience `bff-api` |
| `servico-contratacao-m2m` | confidencial | **Client Credentials** (M2M) |

Roles: `cliente`, `analista`, `operador`.

Validação no BFF (Spring Security Resource Server): busca JWKS do Keycloak, confere assinatura
**RS256**, `iss`, `exp` e **`aud = bff-api`**; mapeia `realm_access.roles` para authorities.

- **200** com token válido e role certa
- **401** sem token ou com assinatura/audience inválida — *não autenticado*
- **403** autenticado com role errada (`cliente` chamando `GET /v1/analises`, que exige `analista`)

**Antipadrões evitados:** password grant, token sem expiração, client secret dentro do SPA — é
exatamente por isso que `app-cliente` é público e usa PKCE.

### 3.8 Observabilidade

Os três sinais via **OpenTelemetry**, um Collector OTLP como ponto único de coleta:

- **Traces** → Tempo
- **Métricas** → Prometheus
- **Logs** → Loki
- **Visualização** → Grafana

Java: agente `opentelemetry-javaagent.jar` (auto-instrumenta Spring MVC, JDBC e Kafka).
Python: `opentelemetry-instrument` com instrumentação FastAPI + Kafka.

**Trace atravessando a fila:** header `traceparent` (W3C) viaja no envelope CloudEvents; a
instrumentação de Kafka injeta no produtor e extrai no consumidor. É o item que mais impressiona
na demo — um trace único indo de `app-cliente` → BFF → crédito → Kafka → risco → Kafka →
antifraude.

**Loki e não ELK:** Loki indexa só labels e guarda o log comprimido; ELK indexa todo o conteúdo.
Para o nosso volume, Loki custa uma fração da memória. **Quando não vale:** busca full-text
arbitrária em log histórico — aí ELK ganha.

Cinco dashboards: latência do BFF (p50/p95/p99), disponibilidade e estado dos circuit breakers,
banco (conexões e duração de query), fila (lag por consumer group, throughput, DLQ) e
cluster/runtime (CPU, memória, heap da JVM, GC).

### 3.9 Implantação e CI/CD

- **Docker**: multi-stage por serviço. Java: `maven:3.9-eclipse-temurin-21` → `eclipse-temurin:21-jre-alpine`. Python: `python:3.12` builder → `python:3.12-slim`.
- **Kubernetes** (`infra/k8s/`): Namespace, Deployment, Service, ConfigMap, Secret, PVC para o
  Postgres, liveness e readiness probes. Alvo: `kind` local. Demo de **self-healing** deletando um
  pod e vendo o ReplicaSet recriar.
- **CI/CD** (GitHub Actions): job `test` rodando Maven e pytest **em paralelo** (automação de teste
  em mais de uma linguagem, item da ementa), job `build` com buildx, `push` para GHCR, `deploy`
  aplicando os manifests num cluster `kind` dentro do runner.

### 3.10 Contratos

- **OpenAPI 3.1** por serviço HTTP (`docs/contratos/openapi-*.yaml`), gerado pelo springdoc no Java
  e pelo FastAPI no Python.
- **AsyncAPI 3.0** (`docs/contratos/asyncapi.yaml`) cobrindo os 6 tópicos: canal, produtor,
  consumidores, schema da carga e headers CloudEvents.
- **Regra de versionamento**: campo novo opcional é compatível e não muda a versão; remover campo,
  renomear campo ou mudar tipo é breaking e cria `.v2` do tópico, com os dois convivendo durante a
  migração.

---

## 4. O que fica de fora, e por quê

Entra no slide de fechamento. Não é lacuna: é escopo decidido.

| Fora | Por quê |
|---|---|
| CQRS completo (2 processos) | Custo operacional não se paga no nosso volume |
| 4 instâncias de Postgres | Isolamento por schema + usuário prova a mesma regra |
| Service mesh, Ingress, HPA | Fora do que as aulas cobriram |
| RAG / componente de IA | Só teoria no encontro 5; entra como slide, não como código |
| GitOps (Argo CD) | Citado na bibliografia, não dado em aula |
| gRPC / Protobuf | Nenhum par de serviços nosso tem contrato quente o bastante |

---

## 5. Cronograma

19 dias corridos até a gravação. Quatro blocos, com **demo funcionando ao fim de cada um** — nada
de integrar tudo na última semana.

### Sprint 0 — Fundação · qui 15/10 → dom 18/10

Objetivo: **todo mundo com o ambiente de pé e os contratos combinados.**

- Repositório criado, estrutura de pastas, CODEOWNERS e proteção do `main`
- `docker-compose.yml` subindo Kafka, Postgres, Keycloak e Kafka UI
- Realm do Keycloak exportado em `infra/keycloak/realm-credito.json`
- `docs/contratos/asyncapi.yaml` com os 6 tópicos fechados — **nenhum serviço começa antes disso**
- Esqueleto dos 5 serviços subindo e respondendo `/health`
- C4 nível 1 (Contexto) e nível 2 (Container)

**Portão de saída:** `docker compose up` sobe tudo e os 5 serviços respondem `/health`.

### Sprint 1 — Domínio e segurança · seg 19/10 → dom 25/10

Objetivo: **fluxo de negócio ponta a ponta, autenticado.**

- Clean/Hexagonal em cada serviço, com a regra de dependência respeitada
- DDD tático no `servico-credito`: Aggregate Root, Value Object, Repository, Domain Event,
  Domain Service
- Testes unitários do domínio **sem framework e sem banco** (prova da regra de dependência)
- Produção e consumo dos 6 tópicos; fluxo completo visível no Kafka UI
- Keycloak integrado: PKCE no SPA, validação JWT no BFF, 200/401/403 funcionando
- Client credentials M2M entre `servico-contratacao` e o mock do core bancário

**Portão de saída:** login no SPA → solicitação → crédito liberado, com 401 e 403 demonstráveis.

### Sprint 2 — Resiliência, CQRS e observabilidade · seg 26/10 → dom 01/11

Objetivo: **as camadas que o professor cobra na demo.**

- CQRS: `proposta_view` populada por evento, `GET /v1/propostas` lendo só dela
- Resilience4j no BFF: circuit breaker, retry só em GET, timeout, bulkhead, fallback
- `/v1` e `/v2` convivendo
- OTel nos 5 serviços, trace atravessando o Kafka
- 5 dashboards no Grafana
- Dockerfiles multi-stage, manifests K8s, pipeline de CI/CD verde

**Portão de saída:** trace único de ponta a ponta visível no Grafana e circuit breaker abrindo ao
derrubar um serviço.

### Fechamento · seg 02/11 → qua 04/11

- **02/11 (feriado)** — *code freeze às 12h*. Tarde: checklist auditado item a item, README
  validado em clone limpo, ensaio cronometrado dos 20 min
- **03/11** — gravação do vídeo. Slides prontos na véspera; demo roda de script, não improvisada
- **04/11** — apresentação

---

## 6. Riscos e plano B

| Risco | Plano B |
|---|---|
| Trace não atravessa o Kafka em Python | Propagar `traceparent` manualmente no header CloudEvents — ~20 linhas |
| Keycloak + PKCE consome a Sprint 1 inteira | Congelar o SPA num fluxo mínimo; PKCE no `curl` com `code_verifier` gerado em script |
| CI/CD não fecha a tempo | Pipeline roda só `test` e `build`; o `deploy` vira `kubectl --dry-run=server` documentado |
| Kubernetes não sobe na máquina de ninguém | Gravar o self-healing em vídeo na Sprint 2, enquanto dá tempo |
| Demo ao vivo falha na apresentação | **A demo é gravada em 03/11.** Esse já é o plano B |
| Alguém do grupo trava na sua trilha | Daily assíncrona no grupo; qualquer trilha parada 48h é redistribuída pelo líder |

---

## 7. Como trabalhamos

- **Branch por trilha**: `trilha/<nome>`, PR para `main`, **1 aprovação** obrigatória
- **`main` sempre verde** — pipeline rodando em todo PR
- **Nada entra sem o "quando não vale"**: PR que adiciona padrão sem ADR é devolvido
- **Daily assíncrona** no grupo até as 10h: o que fiz, o que farei, o que me trava
- **Checkpoint ao vivo** ao fim de cada sprint (dom 18/10, dom 25/10, dom 01/11)
