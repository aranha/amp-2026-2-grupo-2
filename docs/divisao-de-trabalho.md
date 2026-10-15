# Divisão de trabalho

Sete trilhas, uma por integrante. Cada trilha é **dona de um pedaço vertical**: código, contrato,
teste, Dockerfile, ADR e slide. Ninguém depende de outra pessoa para fechar sua própria entrega —
só para combinar contrato.

> Arquitetura e decisões em [plano-de-trabalho.md](plano-de-trabalho.md) ·
> auditoria da entrega em [checklist-entrega.md](checklist-entrega.md)

---

## Quadro geral

| # | Integrante | Trilha | Branch | Entrega central |
|---|---|---|---|---|
| 1 | **Paulo Aranha** *(líder)* | Arquitetura e BFF | `trilha/bff` | BFF agregador, resiliência, C4, consolidação |
| 2 | **Vinicius Eduardo** | Crédito e CQRS | `trilha/credito` | Agregado DDD + read model por evento |
| 3 | **Guilherme** | Risco e CI/CD | `trilha/risco` | Serviço hexagonal + pipeline |
| 4 | **Hugo** | Antifraude (Python) | `trilha/antifraude` | 1º serviço Python no barramento |
| 5 | **Marcus Santos** | Contratação (Python) | `trilha/contratacao` | Contrato, liberação e M2M |
| 6 | **Rafael** | Segurança e identidade | `trilha/seguranca` | Keycloak, PKCE, 401/403 |
| 7 | **Sesaque Cruz** | Observabilidade e K8s | `trilha/plataforma` | OTel, 5 dashboards, Kubernetes |

**Regra comum a todos:** cada um escreve o Dockerfile multi-stage do próprio serviço, o próprio
OpenAPI, os próprios testes de domínio **sem framework e sem banco**, e o próprio slide.

---

## 1 · Paulo Aranha — Arquitetura e BFF  `trilha/bff`

Dono do ponto de integração e da narrativa. É quem aprova PR e quem fecha a apresentação.

**Código**
- `bff/` em Java 21 / Spring Boot
- `GET /v2/propostas/{id}` agregando **três** fontes: proposta (crédito), parecer (risco), status (antifraude)
- Resilience4j: circuit breaker, retry **só em GET**, timeout, bulkhead, fallback degradado
- `/v1` e `/v2` convivendo; v1 com header `Deprecation` + `Sunset`

**Documentos**
- `docs/c4/` — Contexto (N1), Container (N2) e Componente (N3) do BFF
- `docs/arquitetura.md` — consolidação final
- ADR-001 (multiplataforma vs. monolito) · ADR-003 (por que Java **e** Python) · ADR-008 (BFF, resiliência, versionamento)
- `docs/apresentacao/roteiro.md` — roteiro cronometrado dos 20 min

**Coordenação**
- Review e merge de todo PR; `main` sempre verde
- Checkpoint ao vivo ao fim de cada sprint
- Redistribui trilha parada há 48h

**Slides:** bloco 1 (problema, 2 min), bloco 2 (C4, 3 min), bloco 7 (o que não fizemos, 1 min)

---

## 2 · Vinicius Eduardo — Crédito e CQRS  `trilha/credito`

A trilha mais pesada de domínio: é aqui que mora o DDD tático "de verdade" que o enunciado cobra.

**Código** — refatora o `servico-credito` herdado para Clean Architecture
- **Aggregate Root** `SolicitacaoDeCredito` protegendo a invariante → **409** ao aceitar a mesma proposta duas vezes
- **Value Objects** `Dinheiro`, `CPF` · **Repository** por porta · **Domain Event** · **Domain Service** de cálculo de condições
- **CQRS**: tabela `credito.proposta_view` desnormalizada, populada por `risco.elegibilidade.v1`, `antifraude.analise.v1` e `contratacao.contrato.v1`; `GET /v1/propostas/{id}` lê **só** dela
- Schema `credito` com usuário próprio e `GRANT` restrito

**Contrato — bloqueia todo mundo na Sprint 0**
- `docs/contratos/asyncapi.yaml` com os 6 tópicos: canal, produtor, consumidores, schema e headers CloudEvents
- **Prazo rígido: 17/10.** Nenhum serviço começa a produzir evento antes disso

**Documentos**
- ADR-002 (fronteiras de contexto e Database per Service) · ADR-005 (CQRS lite vs. completo)
- C4 Componente do serviço de crédito

**Slides:** bloco 3, parte DDD e CQRS

---

## 3 · Guilherme — Risco e CI/CD  `trilha/risco`

Serviço mais leve (já existe e tem teste), por isso acumula o pipeline.

**Código** — refatora o `servico-risco` herdado para **Hexagonal** (ports & adapters)
- Agregado `AnaliseDeCredito` com a regra de elegibilidade
- Consome `credito.solicitacao.v1`, produz `risco.elegibilidade.v1`
- Revalida a idempotência e a DLQ herdadas da base
- `GET /v1/analises` exigindo role `analista` — **é o endpoint da demo de 403**, combinar com Rafael

**CI/CD** — `.github/workflows/ci.yml`
- Job `test`: Maven e pytest **em paralelo** (automação em mais de uma linguagem, item da ementa)
- Job `build`: buildx dos 5 Dockerfiles multi-stage
- Job `push`: GHCR
- Job `deploy`: `kubectl apply` num `kind` dentro do runner *(plano B: `--dry-run=server`)*

**Documentos**
- ADR-004 — Clean Architecture e Hexagonal: por que são a mesma regra de dependência com nomes diferentes

**Slides:** bloco 3, parte Clean/Hexagonal

---

## 4 · Hugo — Antifraude em Python  `trilha/antifraude`

O serviço que **prova a interoperabilidade**. Se o envelope CloudEvents do Python não bater com o
do Java, o trabalho inteiro perde o eixo de comunicação.

**Código** — `servico-antifraude/` em Python 3.12 / FastAPI
- Clean Architecture em Python: `domain/` sem import de framework
- Agregado `AnaliseAntifraude` com regra de score
- Consome `risco.elegibilidade.v1`, produz `antifraude.analise.v1` e `antifraude.fraude.v1`
- Schema `antifraude` com usuário próprio
- `pytest` do domínio sem FastAPI e sem banco

**Dependência crítica — Sprint 0**
Validar com Vinicius, **até 18/10**, que um evento publicado pelo Python é consumido pelo Java e
vice-versa, com os mesmos headers `ce-*`. É o primeiro risco técnico do projeto.

**Documentos**
- ADR-006 — Kafka como barramento: Observer, partições, consumer groups e chave `solicitacaoId`

**Slides:** bloco 3, parte eventos e Observer

---

## 5 · Marcus Santos — Contratação e Liberação  `trilha/contratacao`

**Código** — `servico-contratacao/` em Python 3.12 / FastAPI
- Agregados `Contrato` e `LiberacaoDeCredito`
- Consome `credito.proposta.v1`; produz `contratacao.contrato.v1` e `contratacao.liberacao.v1`
- **Mock do core bancário** + chamada autenticada por **client credentials (M2M)** — combinar com Rafael
- Retentativa de liberação: o hotspot do EventStorming. Escolher o limite, **justificar no ADR** e parar na DLQ depois dele
- Schema `contratacao` com usuário próprio

**Documentos**
- ADR-007 — CloudEvents e regra de compatibilidade: o que é mudança compatível, o que cria `.v2` de tópico

**Demo**
- Dono do `scripts/demo.sh` — a sequência de `curl` que a gravação segue. Previsível e ensaiada

**Slides:** conduz o bloco 6 (demo, 8 min), com cada dono narrando seu trecho

---

## 6 · Rafael — Segurança e identidade  `trilha/seguranca`

Trilha transversal: entrega sozinho um dos sete blocos da apresentação.

**Infra**
- Keycloak, realm `credito`, exportado em `infra/keycloak/realm-credito.json`
- Clients: `app-cliente` (público, PKCE S256) · `bff` (confidencial, audience `bff-api`) · `servico-contratacao-m2m` (client credentials)
- Roles: `cliente`, `analista`, `operador`

**Código**
- `app-cliente/` — SPA mínima com **Authorization Code + PKCE**, sem nenhum secret
- No `bff/`: Spring Security Resource Server — JWKS, **RS256**, `iss`, `exp` e **`aud = bff-api`**; `realm_access.roles` → authorities
- **200 / 401 / 403** funcionando e demonstráveis

**Prazo rígido:** realm exportado até **18/10** — Paulo e Marcus dependem dele.

**Documentos**
- ADR-009 — Keycloak, OAuth2/OIDC, PKCE e os antipadrões evitados (password grant, token sem expiração, secret no client)
- Slide de LGPD: onde fica o CPF, quem acessa, o que o evento **não** carrega

**Slides:** bloco 4 (segurança, 1,5 min)

---

## 7 · Sesaque Cruz — Observabilidade e Kubernetes  `trilha/plataforma`

Entrega o que mais pesa visualmente na demo: o trace atravessando a fila.

**Observabilidade** — `infra/observabilidade/`
- OTel Collector (OTLP) → **Tempo** (traces), **Prometheus** (métricas), **Loki** (logs), **Grafana**
- Java: agente `opentelemetry-javaagent.jar` nos 3 serviços · Python: `opentelemetry-instrument` nos 2
- **Trace atravessando o Kafka** via `traceparent` W3C no envelope CloudEvents
- **5 dashboards** versionados como JSON: latência do BFF (p50/p95/p99) · disponibilidade e estado dos circuit breakers · banco · fila (lag, throughput, DLQ) · cluster e runtime

**Plataforma**
- `docker-compose.yml` consolidado — sobe **tudo** num comando
- `infra/k8s/`: Namespace, Deployment, Service, ConfigMap, Secret, PVC, liveness e readiness probes
- Demo de **self-healing**: deletar um pod e ver o ReplicaSet recriar. **Gravar na Sprint 2**, enquanto dá tempo

**Documentos**
- ADR-010 — OpenTelemetry e **Loki vs. ELK**: por que Loki no nosso volume e quando ELK ganha
- ADR-011 — Docker multi-stage, Kubernetes e CI/CD *(com Guilherme)*

**Slides:** bloco 5 (observabilidade e implantação, 1,5 min)

---

## Matriz de dependências

Quem trava quem. Tudo que bloqueia está na **Sprint 0** de propósito.

| Entrega | Dono | Prazo | Quem destrava |
|---|---|---|---|
| `asyncapi.yaml` com os 6 tópicos | Vinicius | **17/10** | todos os produtores e consumidores |
| `docker-compose` com Kafka, Postgres e Keycloak | Sesaque | **16/10** | todo mundo, para rodar local |
| Realm `credito` exportado | Rafael | **18/10** | Paulo (JWT no BFF), Marcus (M2M) |
| CloudEvents Python ↔ Java validado | Hugo + Vinicius | **18/10** | Marcus e a integração inteira |
| Schemas e usuários no Postgres | Sesaque | **17/10** | Vinicius, Guilherme, Hugo, Marcus |
| Contrato REST do BFF (`/v1`, `/v2`) | Paulo | **20/10** | Rafael (SPA) |

---

## Divisão da apresentação — 20 min

| Bloco | Tempo | Quem |
|---|---|---|
| 1. Problema e justificativa | 2 min | Paulo |
| 2. C4 (Contexto, Container, Componente) | 3 min | Paulo |
| 3. Padrões: Clean/Hexagonal, DDD, CQRS, Observer, BFF | 3 min | Guilherme · Vinicius · Hugo |
| 4. Segurança: IdP, OAuth2/OIDC, PKCE, JWT | 1,5 min | Rafael |
| 5. Observabilidade e implantação | 1,5 min | Sesaque |
| 6. **Demo** | 8 min | Marcus conduz; cada dono narra seu trecho |
| 7. Fechamento: o que não fizemos e por quê | 1 min | Paulo |

**Roteiro da demo (8 min)** — a ordem é essa, de script, sem improviso:

1. `docker compose up` já de pé — mostrar os containers
2. Login no SPA via PKCE → chamada ao BFF: **200**, **401** sem token, **403** com role errada
3. Solicitação de crédito → fluxo por evento visível no Kafka UI: crédito → risco → antifraude → contratação
4. Invariante do agregado: aceitar a mesma proposta duas vezes → **409**
5. `GET /v1/propostas` lendo o read model populado por evento (CQRS)
6. Derrubar o antifraude → **circuit breaker abre**, BFF degrada com `antifraudeIndisponivel`; subir de volta → fecha
7. `/v1` e `/v2` lado a lado
8. Grafana: **um trace atravessando BFF → crédito → Kafka → risco**, mais o dashboard de latência e os logs correlacionados
9. *(se der tempo)* `kubectl delete pod` → self-healing

---

## Combinados

- **Branch por trilha**, PR para `main`, **1 aprovação** obrigatória
- **PR que adiciona padrão sem ADR é devolvido** — o "quando não vale" é parte do código
- **Daily assíncrona** no grupo até as 10h: o que fiz, o que farei, o que me trava
- **Checkpoints ao vivo**: dom 18/10, dom 25/10, dom 01/11
- **Code freeze: 02/11 às 12h.** Depois disso, só slide e ensaio
