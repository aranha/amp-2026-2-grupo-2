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

### Bloco 3 · Eventos e Observer — Hugo · 1:00

**Slide-âncora:** a tabela "O Observer mapeado" da
[ADR-006](../adr/ADR-006-kafka-como-barramento.md#o-observer-mapeado), com o tópico
`risco.elegibilidade.v1` no centro e os dois consumer groups saindo dele.

| Tempo | Fala |
|---|---|
| 0:00 | "Entre os serviços de domínio não existe chamada HTTP: só eventos no Kafka. É o Observer: o tópico é o *Subject*, e cada consumer group é um observador que se inscreveu." |
| 0:15 | "O risco publica o parecer uma vez e não sabe quem lê. Hoje leem dois: a projeção do crédito e o antifraude, que é Python. O antifraude entrou sem mudar uma linha do risco." |
| 0:30 | "A chave de todo evento é o `solicitacaoId`: tudo de uma solicitação cai na mesma partição, nos três tópicos, seja Java ou Python publicando. É a única ordem que o domínio exige. O custo é a consistência eventual: a proposta aparece antes da análise antifraude, e o BFF mostra o que já chegou." |
| 0:50 | "Quando não vale: quando quem pergunta precisa da resposta na hora. Por isso a consulta do BFF ao antifraude é HTTP." |

### Demo · passo 3 — fluxo por evento · 1:30

O script abre a solicitação pelo BFF e lista, em ordem, os eventos com aquela chave: crédito,
risco e antifraude, todos na mesma partição. Falar enquanto a lista aparece: "nenhum serviço
chamou outro, cada um só publicou". Depois, no Kafka UI, abrir uma mensagem do
`antifraude.analise.v1` e mostrar os headers `ce_*` iguais aos do evento Java logo acima, e o
`traceparent` que leva o trace adiante. Fechar no bloco antifraude do `/v2/propostas`, com o
score e o motivo.

<!-- Cada dono preenche a sua, até 01/11. -->
