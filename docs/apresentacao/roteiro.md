# Roteiro da apresentação — 20 min

> **Esqueleto.** Fechamento por Paulo. Cronometrar no ensaio de **02/11**; gravar em **03/11**.
> Divisão dos blocos e os nove passos da demo em
> [divisao-de-trabalho.md](../divisao-de-trabalho.md#divisão-da-apresentação--20-min).

| Bloco | Tempo | Quem | Acumulado |
|---|---|---|---|
| 1. Problema e justificativa | 2:00 | Paulo | 2:00 |
| 2. C4 | 3:00 | Paulo | 5:00 |
| 3. Padrões | 3:00 | Guilherme · Vinicius · Hugo | 8:00 |
| 4. Segurança | 1:30 | Rafael | 9:30 |
| 5. Observabilidade e implantação | 1:30 | Sesaque | 11:00 |
| 6. Demo | 8:00 | Marcus conduz | 19:00 |
| 7. O que não fizemos e por quê | 1:00 | Paulo | **20:00** |

## Regras do ensaio

- Cada bloco tem **um** slide-âncora. Passar de dois é sinal de que o bloco está longo
- Todo padrão citado vem com **por que existe** e **quando não vale** — é a régua da disciplina
- A demo roda de `scripts/demo.sh`. Nada é digitado ao vivo
- Estourou o tempo no ensaio? Corta de dentro do bloco 3, nunca do bloco 6

## Notas de fala

<!-- Cada dono preenche a sua, até 01/11. -->

### Bloco 3 · Clean/Hexagonal — Guilherme · 1:00

**Slide-âncora:** à esquerda, o hexágono do risco (diagrama do
[README do serviço](../../servico-risco/README.md#o-hexágono)); à direita, a tabela de
equivalência da [ADR-004](../adr/ADR-004-clean-architecture-e-hexagonal.md#a-equivalência), só
com as linhas Entities, Use Cases, Boundaries e Adapters.

| Tempo | Fala |
|---|---|
| 0:00 | "Clean e Hexagonal são a mesma regra com nomes diferentes: toda dependência aponta para dentro. O crédito usa um vocabulário, o risco usa o outro, e a tabela mostra que é a mesma coisa." |
| 0:15 | "No risco, o centro é o agregado `AnaliseDeCredito`, com a regra de elegibilidade. Ele não importa Spring, nem Kafka, nem SQL. Os três consumidores Kafka e o controller são adaptadores de entrada; o JDBC e o publicador do `risco.elegibilidade.v1`, de saída." |
| 0:35 | "Quem garante não é a boa vontade: é um teste de arquitetura que quebra o build se o domínio importar um framework. E o domínio roda sozinho — 19 casos em 34 milissegundos, sem broker e sem banco." |
| 0:50 | "Quando não vale: serviço sem regra. Por isso o BFF não é hexagonal — lá, porta e adaptador seriam camada decorativa." |

### Bloco 5 · CI/CD — Guilherme, dentro do bloco do Sesaque · 0:20

**No slide do Sesaque:** o grafo do GitHub Actions de uma execução na `main`.

"Todo PR roda Maven e pytest em paralelo — teste automatizado nas duas linguagens, cada uma com o
seu gerenciador de dependências — e constrói os seis Dockerfiles multi-stage. Na `main`, a mesma
imagem vai para o GHCR e sobe num cluster kind dentro do próprio runner." (B62, B64, B67 ·
[ADR-011](../adr/ADR-011-docker-kubernetes-ci-cd.md))

### Demo · passo 2 — o que o risco põe na tela

Com `ana.analista`, o `GET /v1/analises` devolve os pareceres reais que o risco acabou de
publicar — não uma lista fixa. Se der tempo no passo 6: com o risco parado, a mesma chamada vira
**503 com `"fonte": "risco"`**, e volta a 200 quando ele sobe.
