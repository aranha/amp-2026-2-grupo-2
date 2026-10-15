# O que veio da disciplina anterior

Este material vem de **Arquitetura Reativa e Event-Driven**, onde o grupo modelou o domínio de
concessão de crédito por EventStorming e implementou dois serviços Java com Kafka.

Reaproveitamos **o domínio e o modelo de eventos**, não a arquitetura — o trabalho desta
disciplina é transformar aquilo numa arquitetura multiplataforma completa.

| Arquivo | O que ainda vale |
|---|---|
| `contratos-de-evento.md` | Envelope CloudEvents, chave de partição e regra de compatibilidade — base do `asyncapi.yaml` |
| `arquitetura-aed.md` | Modelo de domínio, eventos e fronteiras |
| `contratos-da-saga.md`, `saga/` | Saga de reserva de limite e compensação idempotente |
| `adr-aed/` | ADRs de domínio, chave de partição, event sourcing e resiliência |
| `IA-aed.md` | Registro de uso de IA da etapa anterior |

**Perguntar ao professor** se reaproveitar código de outra disciplina é permitido — item F3 do
[checklist](../checklist-entrega.md).
