# Serviço de Antifraude — Python 3.12 / FastAPI

**Dono: Hugo** · `trilha/antifraude` · porta 8084 · schema `antifraude`

O serviço que **prova a interoperabilidade**. Se o envelope CloudEvents do Python não bater com o
do Java, o eixo de comunicação do trabalho cai.

## Estrutura

```
servico-antifraude/
├── domain/        # sem import de framework — é o que o teste prova
├── application/   # casos de uso
├── adapters/      # FastAPI, Kafka, Postgres
└── tests/         # pytest do domínio, sem FastAPI e sem banco
```

## O que entrega

- Agregado `AnaliseAntifraude` com a regra de score
- Consome `risco.elegibilidade.v1` com o grupo `antifraude-analise-v1` · produz `antifraude.analise.v1`
- Schema `antifraude`, usuário `antifraude_user`
- Decisões de barramento, partição e consistência eventual na
  [ADR-006](../docs/adr/ADR-006-kafka-como-barramento.md)

## Como funciona

- Score por faixa do valor aprovado: até R$ 5.000 → 10 · até R$ 20.000 → 30 · até R$ 50.000 → 60 ·
  acima → 90. Aprovada quando o score fica abaixo de 70. O `motivo` diz o score e o limite, por
  exemplo `Score 30 abaixo do limite de aprovação (70)`
- Elegibilidade recusada encerra o fluxo: não há análise nem evento
- Idempotente pelo `ce_id`: a reentrega republica a mesma análise, com o mesmo `eventoId`, e os
  consumidores deduplicam por ele
- Falha de infraestrutura é retentada na mesma posição 4 vezes (0,5 s · 1 s · 2 s · 4 s), sem perder a
  ordem da partição; depois vai para `risco.elegibilidade.v1.dlq` como `TRANSITORIA`. Evento inválido
  vai direto, como `PERMANENTE`. A DLQ guarda a chave, os `ce_*` e o motivo e a origem nos
  cabeçalhos `classificacao` e `kafka_dlt-*`, no mesmo formato dos serviços Java
- Produtor com o particionador `murmur2_random`, o mesmo do cliente Java: a chave `solicitacaoId`
  cai na mesma partição, seja quem for o produtor
- `GET /v1/analises/{solicitacaoId}` devolve a análise mais recente ou 404; é a fonte do status
  antifraude que o BFF agrega. Contrato em `docs/contratos/openapi-antifraude.yaml`
- Traces, métricas e logs saem por OTLP para o `otel-collector` (`opentelemetry-instrument`). O
  processamento continua o trace do `traceparent` recebido e o propaga em tudo o que publica, então
  um único trace atravessa o Kafka; o `/health` fica fora dos traces

## Rodar

```bash
docker compose up -d                                       # na raiz do repositório
docker compose --profile apps up -d servico-antifraude     # http://localhost:8084/health
```

Testes, como no CI (Python 3.12):

```bash
pip install -e "servico-antifraude[test]"
pytest servico-antifraude/tests -v
```

## Interoperabilidade Python ↔ Java

Validada em 25/10 nos dois sentidos, com os mesmos headers `ce_*`: o `risco.elegibilidade.v1`
publicado pelo `servico-risco` (Java) é consumido aqui, e o `antifraude.analise.v1` publicado aqui
preenche a `proposta_view` do `servico-credito` (Java). A mesma chave cai na mesma partição nos
três tópicos. O passo 3 do `scripts/demo.sh` mostra o fluxo.

## A fazer

- [x] Esqueleto com `/health` — **16/10**
- [x] CloudEvents Python ↔ Java validado
- [x] Domínio + pytest sem framework
- [x] Consumidor e produtor Kafka
- [x] `Dockerfile` multi-stage
- [x] `docs/contratos/openapi-antifraude.yaml`
- [x] Retentativa limitada e DLQ
- [x] Consulta para o BFF
- [x] OpenTelemetry com o trace atravessando o Kafka
- [x] Passo 3 do `scripts/demo.sh`
- [x] ADR-006
