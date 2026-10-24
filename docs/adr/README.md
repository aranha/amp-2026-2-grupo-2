# Architecture Decision Records

Onze decisões, uma por padrão que a apresentação defende. Cada uma termina com **quando não vale
a pena** — é a régua que o professor repete em toda aula, e o que separa arquitetura de
*camada decorativa*.

Template: [ADR-000-template.md](ADR-000-template.md)

| ADR | Decisão | Dono | Status |
|---|---|---|---|
| [001](ADR-001-multiplataforma-em-vez-de-monolito.md) | Multiplataforma em vez de monolito | Paulo | ✅ |
| 002 | Fronteiras de contexto e Database per Service | Vinicius | ☐ |
| [003](ADR-003-dois-runtimes-java-e-python.md) | Dois runtimes: Java e Python, e o motivo de cada | Paulo | ✅ |
| 004 | Clean Architecture e Hexagonal: a mesma regra de dependência | Guilherme | ☐ |
| 005 | CQRS *lite* em vez de CQRS completo | Vinicius | ☐ |
| 006 | Kafka como barramento: Observer, partições e consumer groups | Hugo | ☐ |
| 007 | CloudEvents e regra de compatibilidade de contrato | Marcus | ☐ |
| [008](ADR-008-bff-agregacao-resiliencia-versionamento.md) | BFF: agregação, resiliência e versionamento de API | Paulo | ✅ |
| [009](ADR-009-keycloak-oauth2-oidc-pkce.md) | Keycloak, OAuth2/OIDC e PKCE | Rafael | ✅ |
| 010 | OpenTelemetry e Loki em vez de ELK | Sesaque | ☐ |
| 011 | Docker multi-stage, Kubernetes e CI/CD | Sesaque + Guilherme | ☐ |

**Herança.** As ADRs da disciplina anterior, que sustentam o modelo de eventos reaproveitado,
estão em [`../heranca-aed/adr-aed/`](../heranca-aed/adr-aed/). Elas continuam valendo para o
domínio; as onze acima são as decisões **desta** arquitetura.
