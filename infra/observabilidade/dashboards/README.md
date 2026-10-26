# Dashboards do Grafana

Dono: **Sesaque** · provisionados automaticamente na pasta *Concessão de Crédito*
(http://localhost:3000, sem login) · decisão na
[ADR-010](../../../docs/adr/ADR-010-opentelemetry-e-loki.md)

Os cinco que o enunciado cobra (checklist B52–B56):

| Arquivo | Painéis | Fonte |
|---|---|---|
| `01-latencia.json` | p50 / p95 / p99 por rota do BFF, vazão por rota e status, 401/403, p95 das chamadas do BFF aos serviços, p95 por serviço, **traces recentes** e **logs** com link para o trace | agente OTel (`http_server_request_duration`), Tempo, Loki |
| `02-disponibilidade.json` | **mapa de serviços**, alvos `up`, **estado dos circuit breakers**, taxa de erro por serviço, chamadas recusadas pelo breaker, 5xx | Tempo (service graph), actuator do BFF (Resilience4j) |
| `03-banco.json` | conexões por usuário de serviço, pool Hikari, p95 e vazão de query por serviço, queries mais lentas, cache hit, tamanho | `postgres-exporter`, actuator, span metrics do Tempo |
| `04-fila.json` | **lag por consumer group**, vazão por tópico, **profundidade das DLQs**, p95 de processamento por consumidor, membros por grupo | `kafka-exporter`, span metrics do Tempo |
| `05-cluster.json` | CPU e memória por serviço, heap × máximo, tempo em GC, threads, GC do Python, instâncias | agente OTel (JVM), `opentelemetry-instrument` (processo Python) |

Os JSON são gerados a partir de um molde único e versionados aqui — dashboard que só existe na UI
some no `docker compose down -v`. Editar pela UI funciona (`allowUiUpdates`), mas a mudança só
fica se o JSON exportado voltar para esta pasta.

## Como os dados chegam

- **Métricas dos serviços:** OTLP → Collector → endpoint `:8889` → Prometheus. O `job` é o
  `service.name` (`bff`, `servico-credito`, ...).
- **Span metrics e service graph:** o *metrics-generator* do Tempo transforma os traces em
  `traces_spanmetrics_*` e `traces_service_graph_*` e grava no Prometheus por remote write. É o que
  dá a mesma métrica de latência de query para Java e Python.
- **Kafka e Postgres:** `kafka-exporter` e `postgres-exporter`, raspados pelo Prometheus.
- **Circuit breakers e Hikari:** `/actuator/prometheus` dos serviços Java (job `servicos-java`).

Os painéis só mostram dados com tráfego: rode o passo 3 do `scripts/demo.sh` ou faça algumas
solicitações pela SPA antes de abrir.
