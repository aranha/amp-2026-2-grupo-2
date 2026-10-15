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

## Dependência crítica

**Até 18/10**, validar com o Vinicius que um evento publicado pelo Python é consumido pelo Java e
vice-versa, com os mesmos headers `ce-*`. Primeiro risco técnico do projeto.

## A fazer

- [ ] Esqueleto com `/health` — **16/10**
- [ ] CloudEvents Python ↔ Java validado — **18/10**
- [ ] Domínio + pytest sem framework
- [ ] Consumidor e produtor Kafka
- [ ] `Dockerfile` multi-stage · `docs/contratos/openapi-antifraude.yaml`
- [ ] ADR-006
