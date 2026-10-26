# Checklist de entrega

Transcrição item a item do enunciado *Trabalho final — Arquitetura Multiplataforma: o que
entregar*, com **dono** e **onde isso se prova**. Nada é marcado por estar "feito": só marca quem
consegue apontar o artefato ou o passo da demo.

> Arquitetura em [plano-de-trabalho.md](plano-de-trabalho.md) · trilhas em
> [divisao-de-trabalho.md](divisao-de-trabalho.md)

**Auditoria obrigatória: 02/11, logo após o code freeze.** O líder percorre a lista inteira em voz
alta com o grupo. Item sem prova apontável volta a ser `☐`.

---

## Estado em 26/10 — Sprint 2, dia 1

Auditado contra a `main` em `8f21dc7`, mais os passos 6 e 7 da demo, escritos depois e ainda
não commitados. **☑ provado · ◐ parcial · ☐ aberto.**

| | Itens | Onde |
|---|---|---|
| ☑ | **46** | domínio, CQRS, resiliência do BFF e **segurança inteira** |
| ◐ | **19** | diagramas, contratos, observabilidade, Docker, CI, passos 6 e 7 da demo |
| ☐ | **20** | dashboards, Kubernetes, passos 4, 5, 8 e 9 da demo, slides *(+4 opcionais)* |

**Os três bloqueios, em ordem de dano:**

1. **`servico-credito` não tem Dockerfile nem entrada no `docker-compose.yml`.** Derruba a CI da
   `main` no job `Build (servico-credito)`, e com ela o `push` e o `deploy`, que nunca rodaram.
   Sem o container, `docker compose --profile apps up` sobe 4 dos 5 serviços, o `CREDITO_URL` do
   BFF aponta para o vazio e os passos 4 e 5 da demo não têm o que demonstrar. — **Vinicius**
2. **`infra/k8s/` e `infra/observabilidade/dashboards/` têm só README.** Zero manifesto, zero
   JSON. São 10 itens do enunciado (B52–B56, B58–B61) e os passos 8 e 9 da demo. — **Sesaque**
3. **Nenhum serviço Java exporta OTel.** O `x-otel` que o compose cita na linha 180 não existe, e
   só o `servico-antifraude` tem as variáveis `OTEL_*`. Sem isso não há trace atravessando a fila
   (B51) e o scrape dos serviços Java segue comentado no `prometheus.yml`. — **Sesaque**

Aberturas menores, cada uma de um dono: `ADR-010` e `openapi-credito.yaml` não existem; faltam
três dos quatro diagramas C4; `arquitetura.md` ainda é esqueleto — o conteúdo está no
`plano-de-trabalho.md`.

---

## A · Formato da entrega

| # | Item | Dono | Prova |
|---|---|---|---|
| A1 | ☐ Apresentação de **até 20 min**, teoria + demo rodando | Paulo | `docs/apresentacao/roteiro.md` tem notas de fala de 4 blocos; falta o resto e o ensaio cronometrado de 02/11 |
| A2 | ☐ Slides postados no Canvas | Paulo | nenhum slide no repositório |
| A3 | ☐ Vídeo gravado (03/11) e postado | Marcus | gravação |
| A4 | ☑ Código no repositório, público | Paulo | `github.com/aranha/amp-2026-2-grupo-2`, público |
| A5 | ◐ Diagramas postados | Paulo | só `n3-componente-credito.drawio` + `.png`; faltam N1, N2 e N3 do BFF |
| A6 | ◐ **Toda** escolha tecnológica justificada por trade-off | todos | 10 ADRs escritas; **falta a ADR-010** (OTel + Loki vs. ELK) |

---

## B · Parte teórica

### B.1 Visão e justificativa *(encontro 1)*

| # | Item | Dono | Prova |
|---|---|---|---|
| B1 | ◐ Domínio e problema: por que partes do sistema têm necessidades diferentes (leitura rápida, consistência, baixa latência, alta disponibilidade) | Paulo | conteúdo em `plano-de-trabalho.md` §2; `arquitetura.md` §1 ainda é comentário |
| B2 | ☑ Por que multiplataforma e **não** monolito, com trade-off explícito | Paulo | **ADR-001** |
| B3 | ☑ Pelo menos **duas linguagens/runtimes** e o motivo de cada | Paulo | **ADR-003** · Java em `bff`, `servico-credito`, `servico-risco`; Python em `servico-antifraude`, `servico-contratacao` |
| B4 | ◐ Eixo **comunicação**: síncrona e assíncrona | Paulo | `plano-de-trabalho.md` §3.2 e §3.3; `arquitetura.md` §3 vazio |
| B5 | ☑ Eixo **dados**: Database per Service | Vinicius | **ADR-002** · `infra/postgres/init.sql`: 4 schemas, 4 usuários |
| B6 | ◐ Eixo **implantação**: containers e orquestração | Sesaque | **ADR-011** escrita; `infra/k8s/` só tem README |
| B7 | ☐ **C4 nível 1** — Contexto | Paulo | `n1-contexto.*` não existe |
| B8 | ☐ **C4 nível 2** — Container | Paulo | `n2-container.*` não existe |
| B9 | ◐ **C4 nível 3** — Componente de pelo menos um serviço | Paulo + Vinicius | `n3-componente-credito` ☑ com `README-n3-credito.md`; `n3-componente-bff` ☐ |
| B10 | ◐ **OpenAPI** para REST | cada dono | 4 de 5: `bff` (v1, v2, analises), `risco`, `antifraude`, `contratacao` *(só `/health`)* · **falta `openapi-credito.yaml`** |
| B11 | ☑ **AsyncAPI** para eventos | Vinicius | `asyncapi.yaml` · 6 tópicos + 2 DLQ, com `operations` por consumer group |
| B12 | ☑ **Regra de versionamento** declarada | Marcus | **ADR-007** |
| B13 | ☑ Antipadrão **distributed monolith** — evitado e explicado | Paulo | zero chamada síncrona entre serviços de domínio: só o BFF chama REST, o resto é Kafka |
| B14 | ☑ Antipadrão **fragmentação excessiva** — evitado e explicado | Paulo | Contratação + Liberação fundidos · **ADR-002** |
| B15 | ☐ *(opcional)* Team Topologies e Lei de Conway | Paulo | slide, se sobrar tempo |
| B16 | ☐ *(opcional)* O "antes" monolítico | — | fora de escopo, declarado em `plano-de-trabalho.md` §4 |

### B.2 Padrões de arquitetura interna *(encontros 2 e 3)*

Bloco inteiro provado. Único da lista que fecha sem ressalva junto com B.3 e B.4.

| # | Item | Dono | Prova |
|---|---|---|---|
| B17 | ☑ Clean Architecture **ou** Hexagonal, com a **regra de dependência** | Guilherme | **ADR-004** · `RegraDeDependenciaTest` no risco |
| B18 | ☑ A **equivalência** entre as duas, explicada | Guilherme | **ADR-004** · Clean no crédito, Hexagonal no risco (`application/port/in` e `out`) |
| B19 | ☑ DDD tático num serviço com **regra de negócio real** | Vinicius | `servico-credito/domain/` |
| B20 | ☑ **Aggregate Root** | Vinicius | `SolicitacaoDeCredito` · `PropostaJaAceitaException` guarda a invariante |
| B21 | ☑ **Value Object** | Vinicius | `Dinheiro`, `CPF`, `CondicoesDeCredito` |
| B22 | ☑ **Repository** | Vinicius | porta `SolicitacaoDeCreditoRepository` + `JdbcPropostaRepository` |
| B23 | ☑ **Domain Event** | Vinicius | `CreditoSolicitadoEvent`, `ElegibilidadeAprovadaEvent`, `LimiteDeCreditoReservadoEvent` e mais 3 |
| B24 | ☑ **Domain Service** | Vinicius | `CalculadoraCondicoesCredito` + teste sem framework |
| B25 | ☑ **Bounded Context** | Vinicius | 4 contextos, 4 schemas · **ADR-002** |
| B26 | ☑ **CQRS**: Commands e Queries separados | Vinicius | **ADR-005** · `credito.proposta_view` populada por `PropostaProjectionService` |
| B27 | ☑ **Observer → Kafka**: tópico = Subject, consumer group = attach | Hugo | **ADR-006** |

### B.3 Comunicação e integração *(encontros 3 e 5)*

| # | Item | Dono | Prova |
|---|---|---|---|
| B28 | ☑ Lista de **tópicos**: quem publica, quem consome, consumer groups | Hugo | `asyncapi.yaml` · `plano-de-trabalho.md` §3.3 |
| B29 | ☑ **Consistência eventual** explicada | Hugo | **ADR-006** |
| B30 | ☑ **BFF**: por que existe e **quantas fontes agrega** | Paulo | **ADR-008** · `AgregadorDePropostas` chama crédito, risco e antifraude |
| B31 | ☑ O que o BFF faz **além de repassar** | Paulo | agrega 3 fontes, resiliência por downstream, versionamento v1/v2 |
| B32 | ☑ **Circuit Breaker** | Paulo | Resilience4j, 3 instâncias (`credito`, `risco`, `antifraude`), 50% em janela de 10 · `CircuitBreakerDoAntifraudeTest` |
| B33 | ☑ **Retry só em operação idempotente** | Paulo | `PoliticaDeRetryTest` · `POST /v1/solicitacoes` fora do retry |
| B34 | ☑ **Timeout** | Paulo | `application.yml`: 2s conexão / 3s leitura |
| B35 | ☑ **Bulkhead** | Paulo | `maxConcurrentCalls: 10`, `maxWaitDuration: 0` por downstream |
| B36 | ☑ O que é **breaking change** e como v1 e v2 convivem | Paulo | **ADR-008** · `PropostaV1Controller` e `PropostaV2Controller` · headers `Deprecation` e `Sunset` |

### B.4 Segurança *(encontros 2 e 3)*

Bloco inteiro provado — o único que já está pronto para a auditoria de 02/11.

| # | Item | Dono | Prova |
|---|---|---|---|
| B37 | ☑ **Identity Provider** com OAuth2 + OIDC | Rafael | `infra/keycloak/realm-credito.json`, realm `credito` |
| B38 | ☑ **Authorization Code + PKCE** para client público | Rafael | client `app-cliente`: `publicClient: true`, `pkce.code.challenge.method: S256` |
| B39 | ☑ **Client Credentials** para M2M | Rafael + Marcus | client `servico-contratacao-m2m` + `ClientCredentialsToken` em `adapters/core_bank.py` |
| B40 | ☑ Validação do JWT no BFF: **JWKS** | Rafael | `SecurityConfig`, Spring Security Resource Server |
| B41 | ☑ Validação: **assinatura** RS256 | Rafael | `defaultSignatureAlgorithm: RS256` no realm |
| B42 | ☑ Validação: **audience** | Rafael | `ValidadorDeAudience` + `ValidadorDeAudienceTest` · `aud = bff-api` |
| B43 | ☑ Validação: **roles** | Rafael | `ConversorDeRolesDoRealm` + teste · `realm_access.roles` → authorities |
| B44 | ☑ **401 vs. 403** demonstrados | Rafael + Guilherme | `SegurancaDaApiTest` · demo passo 2 implementado em `scripts/demo.sh` |
| B45 | ☑ Antipadrão **password grant** — evitado | Rafael | **ADR-009** · `directAccessGrantsEnabled: false` nos clients do trabalho |
| B46 | ☑ Antipadrão **token sem expiração** — evitado | Rafael | **ADR-009** · `accessTokenLifespan: 300`, sessão 30 min ociosa |
| B47 | ☑ Antipadrão **secret no client** — evitado | Rafael | SPA é client público, por isso PKCE · `app-cliente/src/auth.js` |

### B.5 Observabilidade e operação *(encontro 4)*

**O bloco mais atrasado: 10 itens abertos.** A stack está de pé no compose e o Collector tem as
três pipelines; o que falta é instrumentar os serviços e versionar manifesto e dashboard.

| # | Item | Dono | Prova |
|---|---|---|---|
| B48 | ◐ **Traces** com OTel, e para onde vão | Sesaque | pipeline `otlp → tempo` no `otel-collector.yaml`; **só o antifraude exporta** — nenhum serviço Java tem `OTEL_*` |
| B49 | ◐ **Métricas** com OTel | Sesaque | pipeline `otlp → prometheus`; o BFF expõe `/actuator/prometheus`, mas o scrape dos Java segue **comentado** no `prometheus.yml` |
| B50 | ◐ **Logs** com OTel | Sesaque | pipeline `otlphttp/loki` + `derivedFields` de TraceID no datasource; **falta a ADR-010** (Loki vs. ELK) |
| B51 | ☐ **Trace atravessando a mensageria** | Sesaque | `traceparent` está no envelope do `asyncapi.yaml` e no `test_consumo.py` do antifraude, mas nenhum produtor Java o propaga |
| B52 | ☐ Dashboard de **latência** | Sesaque | `dashboards/01-latencia.json` não existe |
| B53 | ☐ Dashboard de **disponibilidade** | Sesaque | `02-disponibilidade.json` não existe |
| B54 | ☐ Dashboard de **banco** | Sesaque | `03-banco.json` não existe |
| B55 | ☐ Dashboard de **fila** | Sesaque | `04-fila.json` não existe |
| B56 | ☐ Dashboard de **cluster** | Sesaque | `05-cluster.json` não existe |
| B57 | ◐ **Docker multi-stage** | cada dono | 5 Dockerfiles, todos com 2 estágios · **`servico-credito` não tem o seu** — é o que quebra a CI |
| B58 | ☐ K8s: **Deployment** | Sesaque | `infra/k8s/` só tem README |
| B59 | ☐ K8s: **Service** | Sesaque | idem |
| B60 | ☐ K8s: **ConfigMap e Secret** | Sesaque | idem |
| B61 | ☐ K8s: **self-healing** | Sesaque | depende de B58 |
| B62 | ◐ **CI/CD**: teste, build, push e deploy | Guilherme | `ci.yml` tem os 4 jobs; **`main` vermelha** no build do crédito, então `push` e `deploy` nunca executaram |

### B.6 Itens da ementa sem aula própria — citar em um slide

Dependem dos slides, que ainda não existem. Onde a implementação já prova o ponto, está marcado
◐: falta só a citação.

| # | Item | Dono | Prova |
|---|---|---|---|
| B63 | ☐ **Estado e sessão**: stateless via JWT, estado em cada serviço | Rafael | BFF é `SessionCreationPolicy.STATELESS`; falta o slide 4 |
| B64 | ◐ **Dependências por plataforma**: Maven e pip/poetry | Guilherme | jobs `teste-java` (Maven) e `teste-python` (pip), com cache por plataforma; falta o slide 5 |
| B65 | ☑ **Interoperabilidade entre linguagens** | Hugo | `ce_specversion` idêntico em `adapters/kafka.py`, `adapters/cloudevents.py` e nos publicadores Java |
| B66 | ◐ **Compliance / LGPD**: onde ficam os dados sensíveis e quem acessa | Rafael | e-mail fora do access token (`721fecf`), CPF não trafega no `asyncapi.yaml`, tratado na **ADR-009**; falta o slide 4 |
| B67 | ☑ **Testes automatizados em mais de uma linguagem, no pipeline** | Guilherme | `teste-java` (3 serviços) ∥ `teste-python` (2), **verdes na `main`** |
| B68 | ☐ **GitOps** — citado | Sesaque | slide 7, como o que ficou de fora |
| B69 | ☐ *(opcional)* **RAG**: dono dos embeddings e sincronização por evento | Paulo | slide 7, só teoria |

---

## C · Parte prática — o mínimo defensável

| # | Item | Dono | Prova |
|---|---|---|---|
| C1 | ◐ **3 a 4 serviços de domínio + 1 BFF** | todos | os 4 + BFF existem no repositório; **`servico-credito` não containeriza** |
| C2 | ☑ Em **pelo menos 2 linguagens** | todos | Java ×3, Python ×2 |
| C3 | ☑ Cada um com **seu schema ou banco** | Sesaque | `init.sql`: 4 schemas, 4 usuários, `GRANT` restrito |
| C4 | ◐ Um `docker compose up` sobe **tudo**: IdP, broker e stack de observabilidade | Sesaque | infra completa ☑ (Kafka, Postgres, Keycloak, Collector, Tempo, Loki, Prometheus, Grafana) · `--profile apps` sobe **4 dos 5** serviços |
| C5 | ☑ Testes unitários do domínio **sem framework nem banco** | cada dono | `SolicitacaoDeCreditoTest`, `CalculadoraCondicoesCreditoTest`, `RegraDeDependenciaTest`, `test_dominio.py` — verdes no CI |

### Os nove passos da demo

`scripts/demo.sh` executa os passos 1, 2, 3, 6 e 7. Os quatro que faltam são `# TODO` no
script — dois do Vinicius, dois do Sesaque.

| # | Passo | Dono | Tempo | Estado |
|---|---|---|---|---|
| D1 | ◐ `docker compose up` de pé, containers à vista | Sesaque | 0:30 | `docker compose ps` no script; faltará o crédito na tela |
| D2 | ☑ Login no Keycloak → BFF com **200**, **401** sem token, **403** com role errada | Rafael | 1:30 | implementado, com plano B por `TOKEN_ANA`/`TOKEN_MARIA` |
| D3 | ☑ Fluxo ponta a ponta por evento: A publica, B e C consomem, visível no Kafka UI | Hugo | 1:30 | implementado: consome os tópicos e imprime `ce_type`, `ce_source` e partição |
| D4 | ☐ Regra de domínio protegida pelo agregado — **409** ao aceitar duas vezes | Vinicius | 0:45 | `# TODO` |
| D5 | ☐ Commands e Queries separados, leitura populada por evento | Vinicius | 0:45 | `# TODO` |
| D6 | ◐ **Circuit breaker abrindo** ao derrubar um serviço e **fechando** quando volta | Paulo | 1:00 | escrito: derruba o antifraude, 10 chamadas, estado pelo `/actuator/health` · `CircuitBreakerDoAntifraudeTest` prova o comportamento · **não rodou ponta a ponta** |
| D7 | ◐ Rotas **`/v1` e `/v2`** convivendo | Paulo | 0:30 | escrito: as duas respostas lado a lado + `Deprecation`/`Sunset` · `VersionamentoDaPropostaTest` · **não rodou ponta a ponta** |
| D8 | ☐ Grafana: **trace atravessando BFF, serviço e fila** + dashboard de latência + logs correlacionados | Sesaque | 1:30 | `# TODO` — bloqueado por B48, B51 e B52 |
| D9 | ☐ *(opcional, mas pesa)* K8s com self-healing + pipeline de CI rodando os testes | Sesaque + Guilherme | 0:30 | `# TODO` — bloqueado por B58 |

**Total: 8:30.** Folga de 30s no bloco de demo.

---

## E · Portões por sprint

Nada avança com o portão anterior aberto.

| Sprint | Até | Portão |
|---|---|---|
| **0 — Fundação** | dom **18/10** | ◐ `docker compose up` sobe tudo e os 5 serviços respondem `/health` — **4 de 5** · ☑ `asyncapi.yaml` fechado · ☑ realm exportado · ☑ CloudEvents Python ↔ Java validado |
| **1 — Domínio e segurança** | dom **25/10** | ◐ Login → solicitação → crédito liberado, ponta a ponta — o encadeamento por evento está no passo 3, mas sem o container do crédito não roda inteiro · ☑ 200/401/403 funcionando · ☑ testes de domínio sem framework verdes |
| **2 — Resiliência e observabilidade** | dom **01/11** | ☐ Trace único atravessando o Kafka no Grafana · ◐ circuit breaker abrindo e fechando — passo escrito e coberto por teste, falta rodar na stack · ◐ `/v1` e `/v2` — passo escrito, falta rodar · ☐ CI verde |
| **Fechamento** | seg **02/11** | ☐ Code freeze 12h · ☐ **esta lista auditada inteira** · ☐ README validado em clone limpo · ☐ ensaio cronometrado ≤ 20 min |
| **Gravação** | ter **03/11** | ☐ Slides prontos · ☐ vídeo gravado |
| **Entrega** | qua **04/11** | ☐ Apresentação · ☐ tudo no Canvas |

**O portão da Sprint 1 fecha hoje e não fechou.** O que falta não é domínio nem segurança — é o
empacotamento do crédito. É o primeiro item da Sprint 2, antes de qualquer coisa nova.

---

## F · Perguntar ao professor

O próprio enunciado levanta estes pontos. Levar na próxima aula — **a resposta muda o peso das
trilhas**, então quanto antes, melhor.

1. ☐ **Como os 100 pontos se dividem** entre teoria, demo e código? O plano da disciplina não traz barema.
2. ☐ O **domínio precisa ser diferente do PizzaExpress**? Assumimos que sim e escolhemos concessão de crédito.
3. ☐ Podemos **reaproveitar código de outra disciplina** como base? *(Os 2 serviços Java vêm de Arquitetura Reativa e Event-Driven.)*
4. ☐ A **aula 6** ainda pode acrescentar itens — reabrir este checklist depois dela.
5. ☐ O vídeo gravado substitui a demo ao vivo **sem perda de nota**?
