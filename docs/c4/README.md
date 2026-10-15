# Diagramas C4

Dono: **Paulo** (N1, N2, N3 do BFF) · **Vinicius** (N3 do crédito)

| Nível | Arquivo | O que mostra |
|---|---|---|
| 1 — Contexto | `n1-contexto.drawio` | Cliente, o sistema, Bureau de Crédito, Sistema Antifraude, Core Bancário |
| 2 — Container | `n2-container.drawio` | SPA, BFF, 4 serviços, Kafka, Postgres, Keycloak, stack OTel |
| 3 — Componente | `n3-componente-bff.drawio` | Camadas do BFF: controller, agregador, clients com Resilience4j |
| 3 — Componente | `n3-componente-credito.drawio` | Clean Architecture do crédito: domínio, aplicação, adaptadores |

Exportar cada um também em `.png` para os slides. Checklist B7–B9.
