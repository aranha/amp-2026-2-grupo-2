# Dashboards do Grafana

Dono: **Sesaque** · provisionados automaticamente na pasta *Concessão de Crédito*.

Os cinco que o enunciado cobra (checklist B52–B56):

| Arquivo | Painéis |
|---|---|
| `01-latencia.json` | p50 / p95 / p99 por rota do BFF, throughput |
| `02-disponibilidade.json` | Taxa de erro por serviço, **estado dos circuit breakers** |
| `03-banco.json` | Conexões ativas, duração de query, pool por serviço |
| `04-fila.json` | **Lag por consumer group**, throughput por tópico, profundidade da DLQ |
| `05-cluster.json` | CPU e memória por container, heap da JVM, GC |

Versionar o JSON exportado — dashboard que só existe na UI some no `docker compose down -v`.
