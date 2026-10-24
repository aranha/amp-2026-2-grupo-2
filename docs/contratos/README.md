# Contratos

| Arquivo | Dono | Prazo |
|---|---|---|
| `asyncapi.yaml` — 6 tópicos: canal, produtor, consumidores, schema, headers CloudEvents | Vinicius | **17/10** |
| [`openapi-bff.yaml`](openapi-bff.yaml) — `/v1/propostas`, `/v2/propostas`, `/v1/analises` | Paulo | Sprint 1 ✅ |
| `openapi-credito.yaml` | Vinicius | Sprint 1 |
| `openapi-risco.yaml` | Guilherme | Sprint 1 |
| `openapi-antifraude.yaml` | Hugo | Sprint 1 |
| `openapi-contratacao.yaml` | Marcus | Sprint 1 |

**O `asyncapi.yaml` bloqueia todo mundo.** Nenhum serviço produz evento antes de ele estar
fechado — é o portão da Sprint 0.

Regra de versionamento em [ADR-007](../adr/README.md). Base herdada do envelope CloudEvents em
[`../heranca-aed/contratos-de-evento.md`](../heranca-aed/contratos-de-evento.md).
