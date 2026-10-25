# Serviço de Contratação e Liberação — Python 3.12 / FastAPI

**Dono: Marcus Santos** · `trilha/contratacao` · porta 8085 · schema `contratacao`

Contratação e Liberação fundidos de propósito: as duas etapas mudam pelo mesmo motivo. A fusão é
o exemplo de **fragmentação excessiva evitada** na apresentação.

## O que entrega

- Agregados `Contrato` e `LiberacaoDeCredito`
- Consome `credito.proposta.v1` · produz `contratacao.contrato.v1` e `contratacao.liberacao.v1`
- Mock local determinístico do core bancário, com cliente HTTP opcional autenticado por
  **client credentials (M2M)** *(com Rafael)*
- Retentativa de liberação limitada a três tentativas, com estado `RETRYING` e `DLQ`
- `scripts/demo.sh` — a sequência de `curl` que a gravação segue

## A fazer

- [x] Esqueleto com `/health` — **16/10**
- [x] Domínio + pytest sem framework
- [x] Consumidor e produtores Kafka com CloudEvents binary mode
- [x] Mock local do core bancário + cliente HTTP com M2M opcional
- [x] Retentativa de liberação limitada e publicação do estado na DLQ
- [x] `Dockerfile` multi-stage · `docs/contratos/openapi-contratacao.yaml` (contrato preliminar)
- [x] ADR-007 · script de demo compartilhado

## Estado da primeira etapa

O serviço possui domínio isolado, consumidor e produtores Kafka, persistência no schema próprio,
idempotência por `ce_id`, endpoint `/health`, imagem multi-stage e testes dos fluxos de sucesso,
retry e DLQ. O modo local usa um mock determinístico; o modo HTTP usa Client Credentials e exige
as variáveis `CONTRACTING_CORE_BANK_URL`, `CONTRACTING_OIDC_ISSUER`,
`CONTRACTING_OIDC_CLIENT_ID` e `CONTRACTING_OIDC_CLIENT_SECRET`.
Em execução local sem Kafka, `CONTRACTING_ENABLE_KAFKA=false` mantém apenas o endpoint operacional;
o Compose habilita o consumidor explicitamente.
