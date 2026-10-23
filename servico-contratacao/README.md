# Serviço de Contratação e Liberação — Python 3.12 / FastAPI

**Dono: Marcus Santos** · `trilha/contratacao` · porta 8085 · schema `contratacao`

Contratação e Liberação fundidos de propósito: as duas etapas mudam pelo mesmo motivo. A fusão é
o exemplo de **fragmentação excessiva evitada** na apresentação.

## O que entrega

- Agregados `Contrato` e `LiberacaoDeCredito`
- Consome `credito.proposta.v1` · produz `contratacao.contrato.v1` e `contratacao.liberacao.v1`
- **Mock do core bancário** + chamada autenticada por **client credentials (M2M)** *(com Rafael)*
- Retentativa de liberação: o hotspot do EventStorming. Escolher o limite, justificar na ADR-007
  e parar na DLQ depois dele
- `scripts/demo.sh` — a sequência de `curl` que a gravação segue

## A fazer

- [x] Esqueleto com `/health` — **16/10**
- [x] Domínio + pytest sem framework
- [ ] Consumidor e produtores Kafka
- [ ] Mock do core bancário + M2M
- [x] `Dockerfile` multi-stage · `docs/contratos/openapi-contratacao.yaml` (contrato preliminar)
- [ ] ADR-007 · `scripts/demo.sh`

## Estado da primeira etapa

O serviço já possui domínio isolado, testes unitários, portas para os adaptadores, migração
inicial do schema próprio, endpoint `/health` e imagem multi-stage. A integração Kafka, o mock
do core, Client Credentials e os payloads finais permanecem pendentes do aceite conjunto
registrado no plano de trabalho.
