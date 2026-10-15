# Checklist de entrega

Transcrição item a item do enunciado *Trabalho final — Arquitetura Multiplataforma: o que
entregar*, com **dono** e **onde isso se prova**. Nada é marcado por estar "feito": só marca quem
consegue apontar o artefato ou o passo da demo.

> Arquitetura em [plano-de-trabalho.md](plano-de-trabalho.md) · trilhas em
> [divisao-de-trabalho.md](divisao-de-trabalho.md)

**Auditoria obrigatória: 02/11, logo após o code freeze.** O líder percorre a lista inteira em voz
alta com o grupo. Item sem prova apontável volta a ser `[ ]`.

---

## A · Formato da entrega

| # | Item | Dono | Prova |
|---|---|---|---|
| A1 | ☐ Apresentação de **até 20 min**, teoria + demo rodando | Paulo | `docs/apresentacao/roteiro.md` cronometrado no ensaio de 02/11 |
| A2 | ☐ Slides postados no Canvas | Paulo | arquivo final |
| A3 | ☐ Vídeo gravado (03/11) e postado | Marcus | gravação |
| A4 | ☐ Código no repositório, público | Paulo | repositório |
| A5 | ☐ Diagramas postados | Paulo | `docs/c4/` |
| A6 | ☐ **Toda** escolha tecnológica justificada por trade-off | todos | `docs/adr/` — 11 ADRs |

---

## B · Parte teórica

### B.1 Visão e justificativa *(encontro 1)*

| # | Item | Dono | Prova |
|---|---|---|---|
| B1 | ☐ Domínio e problema: por que partes do sistema têm necessidades diferentes (leitura rápida, consistência, baixa latência, alta disponibilidade) | Paulo | slide 1 · `arquitetura.md` §1 |
| B2 | ☐ Por que multiplataforma e **não** monolito, com trade-off explícito | Paulo | **ADR-001** |
| B3 | ☐ Pelo menos **duas linguagens/runtimes** e o motivo de cada | Paulo | **ADR-003** · Java em 3 serviços, Python em 2 |
| B4 | ☐ Eixo **comunicação**: síncrona e assíncrona | Paulo | `arquitetura.md` §3.2 |
| B5 | ☐ Eixo **dados**: Database per Service | Vinicius | **ADR-002** · schema + usuário por serviço |
| B6 | ☐ Eixo **implantação**: containers e orquestração | Sesaque | **ADR-011** · `infra/k8s/` |
| B7 | ☐ **C4 nível 1** — Contexto | Paulo | `docs/c4/n1-contexto.*` |
| B8 | ☐ **C4 nível 2** — Container | Paulo | `docs/c4/n2-container.*` |
| B9 | ☐ **C4 nível 3** — Componente de pelo menos um serviço | Paulo + Vinicius | `docs/c4/n3-componente-bff.*` e `n3-componente-credito.*` |
| B10 | ☐ **OpenAPI** para REST | cada dono | `docs/contratos/openapi-*.yaml` (5) |
| B11 | ☐ **AsyncAPI** para eventos | Vinicius | `docs/contratos/asyncapi.yaml` |
| B12 | ☐ **Regra de versionamento** declarada | Marcus | **ADR-007** |
| B13 | ☐ Antipadrão **distributed monolith** — evitado e explicado | Paulo | zero chamada síncrona entre serviços de domínio |
| B14 | ☐ Antipadrão **fragmentação excessiva** — evitado e explicado | Paulo | Contratação + Liberação fundidos · **ADR-002** |
| B15 | ☐ *(opcional)* Team Topologies e Lei de Conway | Paulo | slide, se sobrar tempo |
| B16 | ☐ *(opcional)* O "antes" monolítico | — | fora de escopo, declarado no bloco 7 |

### B.2 Padrões de arquitetura interna *(encontros 2 e 3)*

| # | Item | Dono | Prova |
|---|---|---|---|
| B17 | ☐ Clean Architecture **ou** Hexagonal, com a **regra de dependência** | Guilherme | **ADR-004** |
| B18 | ☐ A **equivalência** entre as duas, explicada | Guilherme | **ADR-004** · Clean no crédito, Hexagonal no risco |
| B19 | ☐ DDD tático num serviço com **regra de negócio real** | Vinicius | `servico-credito` |
| B20 | ☐ **Aggregate Root** | Vinicius | `SolicitacaoDeCredito` |
| B21 | ☐ **Value Object** | Vinicius | `Dinheiro`, `CPF` |
| B22 | ☐ **Repository** | Vinicius | porta + adaptador JDBC |
| B23 | ☐ **Domain Event** | Vinicius | eventos do agregado |
| B24 | ☐ **Domain Service** | Vinicius | cálculo de condições de crédito |
| B25 | ☐ **Bounded Context** | Vinicius | 4 contextos, 4 schemas · **ADR-002** |
| B26 | ☐ **CQRS**: Commands e Queries separados | Vinicius | **ADR-005** · `proposta_view` por evento |
| B27 | ☐ **Observer → Kafka**: tópico = Subject, consumer group = attach | Hugo | **ADR-006** |

### B.3 Comunicação e integração *(encontros 3 e 5)*

| # | Item | Dono | Prova |
|---|---|---|---|
| B28 | ☐ Lista de **tópicos**: quem publica, quem consome, consumer groups | Hugo | `asyncapi.yaml` · `plano-de-trabalho.md` §3.3 |
| B29 | ☐ **Consistência eventual** explicada | Hugo | **ADR-006** |
| B30 | ☐ **BFF**: por que existe e **quantas fontes agrega** | Paulo | **ADR-008** · `/v2/propostas` agrega 3 |
| B31 | ☐ O que o BFF faz **além de repassar** | Paulo | agrega, resiliência, versionamento |
| B32 | ☐ **Circuit Breaker** | Paulo | Resilience4j, por downstream |
| B33 | ☐ **Retry só em operação idempotente** | Paulo | só GET — nunca em `POST /solicitacoes` |
| B34 | ☐ **Timeout** | Paulo | 2s connect / 3s read |
| B35 | ☐ **Bulkhead** | Paulo | semáforo 10 por downstream |
| B36 | ☐ O que é **breaking change** e como v1 e v2 convivem | Paulo | **ADR-008** · `/v1` e `/v2` de propostas |

### B.4 Segurança *(encontros 2 e 3)*

| # | Item | Dono | Prova |
|---|---|---|---|
| B37 | ☐ **Identity Provider** com OAuth2 + OIDC | Rafael | Keycloak, realm `credito` |
| B38 | ☐ **Authorization Code + PKCE** para client público | Rafael | `app-cliente`, S256 |
| B39 | ☐ **Client Credentials** para M2M | Rafael + Marcus | `servico-contratacao-m2m` → core bancário |
| B40 | ☐ Validação do JWT no BFF: **JWKS** | Rafael | Spring Security Resource Server |
| B41 | ☐ Validação: **assinatura** RS256 | Rafael | config do BFF |
| B42 | ☐ Validação: **audience** | Rafael | `aud = bff-api` |
| B43 | ☐ Validação: **roles** | Rafael | `realm_access.roles` → authorities |
| B44 | ☐ **401 vs. 403** demonstrados | Rafael + Guilherme | demo passo 2 · `GET /v1/analises` exige `analista` |
| B45 | ☐ Antipadrão **password grant** — evitado | Rafael | **ADR-009** |
| B46 | ☐ Antipadrão **token sem expiração** — evitado | Rafael | **ADR-009** |
| B47 | ☐ Antipadrão **secret no client** — evitado | Rafael | SPA é client público, por isso PKCE |

### B.5 Observabilidade e operação *(encontro 4)*

| # | Item | Dono | Prova |
|---|---|---|---|
| B48 | ☐ **Traces** com OTel, e para onde vão | Sesaque | Collector → Tempo |
| B49 | ☐ **Métricas** com OTel | Sesaque | Collector → Prometheus |
| B50 | ☐ **Logs** com OTel | Sesaque | Collector → Loki · **ADR-010** (Loki vs ELK) |
| B51 | ☐ **Trace atravessando a mensageria** | Sesaque | `traceparent` W3C no envelope CloudEvents |
| B52 | ☐ Dashboard de **latência** | Sesaque | Grafana, p50/p95/p99 do BFF |
| B53 | ☐ Dashboard de **disponibilidade** | Sesaque | erro + estado dos circuit breakers |
| B54 | ☐ Dashboard de **banco** | Sesaque | conexões, duração de query |
| B55 | ☐ Dashboard de **fila** | Sesaque | lag por consumer group, throughput, DLQ |
| B56 | ☐ Dashboard de **cluster** | Sesaque | CPU, memória, heap JVM, GC |
| B57 | ☐ **Docker multi-stage** | cada dono | 5 Dockerfiles |
| B58 | ☐ K8s: **Deployment** | Sesaque | `infra/k8s/` |
| B59 | ☐ K8s: **Service** | Sesaque | `infra/k8s/` |
| B60 | ☐ K8s: **ConfigMap e Secret** | Sesaque | `infra/k8s/` |
| B61 | ☐ K8s: **self-healing** | Sesaque | `kubectl delete pod` → recriado |
| B62 | ☐ **CI/CD**: teste, build, push e deploy | Guilherme | `.github/workflows/ci.yml` |

### B.6 Itens da ementa sem aula própria — citar em um slide

| # | Item | Dono | Prova |
|---|---|---|---|
| B63 | ☐ **Estado e sessão**: stateless via JWT, estado em cada serviço | Rafael | slide 4 |
| B64 | ☐ **Dependências por plataforma**: Maven e pip/poetry | Guilherme | slide 5 · os dois no CI |
| B65 | ☐ **Interoperabilidade entre linguagens** | Hugo | CloudEvents idêntico em Java e Python |
| B66 | ☐ **Compliance / LGPD**: onde ficam os dados sensíveis e quem acessa | Rafael | slide 4 · CPF não trafega no evento |
| B67 | ☐ **Testes automatizados em mais de uma linguagem, no pipeline** | Guilherme | job `test`: Maven ∥ pytest |
| B68 | ☐ **GitOps** — citado | Sesaque | slide 7, como o que ficou de fora |
| B69 | ☐ *(opcional)* **RAG**: dono dos embeddings e sincronização por evento | Paulo | slide 7, só teoria |

---

## C · Parte prática — o mínimo defensável

| # | Item | Dono | Prova |
|---|---|---|---|
| C1 | ☐ **3 a 4 serviços de domínio + 1 BFF** | todos | 4 + BFF |
| C2 | ☐ Em **pelo menos 2 linguagens** | todos | Java ×3, Python ×2 |
| C3 | ☐ Cada um com **seu schema ou banco** | Sesaque | 4 schemas, 4 usuários, `GRANT` restrito |
| C4 | ☐ Um `docker compose up` sobe **tudo**: IdP, broker e stack de observabilidade | Sesaque | `docker-compose.yml` |
| C5 | ☐ Testes unitários do domínio **sem framework nem banco** | cada dono | prova da regra de dependência |

### Os nove passos da demo

| # | Passo | Dono | Tempo |
|---|---|---|---|
| D1 | ☐ `docker compose up` de pé, containers à vista | Sesaque | 0:30 |
| D2 | ☐ Login no Keycloak → BFF com **200**, **401** sem token, **403** com role errada | Rafael | 1:30 |
| D3 | ☐ Fluxo ponta a ponta por evento: A publica, B e C consomem, visível no Kafka UI | Hugo | 1:30 |
| D4 | ☐ Regra de domínio protegida pelo agregado — **409** ao aceitar duas vezes | Vinicius | 0:45 |
| D5 | ☐ Commands e Queries separados, leitura populada por evento | Vinicius | 0:45 |
| D6 | ☐ **Circuit breaker abrindo** ao derrubar um serviço e **fechando** quando volta | Paulo | 1:00 |
| D7 | ☐ Rotas **`/v1` e `/v2`** convivendo | Paulo | 0:30 |
| D8 | ☐ Grafana: **trace atravessando BFF, serviço e fila** + dashboard de latência + logs correlacionados | Sesaque | 1:30 |
| D9 | ☐ *(opcional, mas pesa)* K8s com self-healing + pipeline de CI rodando os testes | Sesaque + Guilherme | 0:30 |

**Total: 8:30.** Folga de 30s no bloco de demo.

---

## E · Portões por sprint

Nada avança com o portão anterior aberto.

| Sprint | Até | Portão |
|---|---|---|
| **0 — Fundação** | dom **18/10** | ☐ `docker compose up` sobe tudo e os 5 serviços respondem `/health` · ☐ `asyncapi.yaml` fechado · ☐ realm exportado · ☐ CloudEvents Python ↔ Java validado |
| **1 — Domínio e segurança** | dom **25/10** | ☐ Login → solicitação → crédito liberado, ponta a ponta · ☐ 200/401/403 funcionando · ☐ testes de domínio sem framework verdes |
| **2 — Resiliência e observabilidade** | dom **01/11** | ☐ Trace único atravessando o Kafka no Grafana · ☐ circuit breaker abrindo e fechando · ☐ `/v1` e `/v2` · ☐ CI verde |
| **Fechamento** | seg **02/11** | ☐ Code freeze 12h · ☐ **esta lista auditada inteira** · ☐ README validado em clone limpo · ☐ ensaio cronometrado ≤ 20 min |
| **Gravação** | ter **03/11** | ☐ Slides prontos · ☐ vídeo gravado |
| **Entrega** | qua **04/11** | ☐ Apresentação · ☐ tudo no Canvas |

---

## F · Perguntar ao professor

O próprio enunciado levanta estes pontos. Levar na próxima aula — **a resposta muda o peso das
trilhas**, então quanto antes, melhor.

1. ☐ **Como os 100 pontos se dividem** entre teoria, demo e código? O plano da disciplina não traz barema.
2. ☐ O **domínio precisa ser diferente do PizzaExpress**? Assumimos que sim e escolhemos concessão de crédito.
3. ☐ Podemos **reaproveitar código de outra disciplina** como base? *(Os 2 serviços Java vêm de Arquitetura Reativa e Event-Driven.)*
4. ☐ A **aula 6** ainda pode acrescentar itens — reabrir este checklist depois dela.
5. ☐ O vídeo gravado substitui a demo ao vivo **sem perda de nota**?
