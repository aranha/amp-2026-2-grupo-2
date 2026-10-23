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
- Consome `risco.elegibilidade.v1` · produz `antifraude.analise.v1` e `antifraude.fraude.v1`
- Schema `antifraude`, usuário `antifraude_user`

## Como funciona

- Score por faixa do valor aprovado: até R$ 5.000 → 10 · até R$ 20.000 → 30 · até R$ 50.000 → 60 ·
  acima → 90. Aprovada quando o score fica abaixo de 70
- Elegibilidade recusada encerra o fluxo: não há análise nem evento
- Idempotente pelo `ce_id`: a reentrega republica a mesma análise, com o mesmo `eventoId`, e os
  consumidores deduplicam por ele
- Evento inválido é descartado com log; falha de infraestrutura é retentada na mesma posição, sem
  perder a ordem da partição
- Produtor com o particionador `murmur2_random`, o mesmo do cliente Java: a chave `solicitacaoId`
  cai na mesma partição, seja quem for o produtor

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

## Dependência crítica

**Até 18/10**, validar com o Vinicius que um evento publicado pelo Python é consumido pelo Java e
vice-versa, com os mesmos headers `ce-*`. Primeiro risco técnico do projeto.

## A fazer

- [x] Esqueleto com `/health` — **16/10**
- [ ] CloudEvents Python ↔ Java validado — **18/10**
- [x] Domínio + pytest sem framework
- [x] Consumidor e produtor Kafka
- [x] `Dockerfile` multi-stage
- [ ] `docs/contratos/openapi-antifraude.yaml`
- [ ] Retentativa limitada e DLQ
- [ ] ADR-006
