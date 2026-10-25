# ADR-006 — Kafka como barramento: Observer, partições e consumer groups

## Status

Proposto — aceite no PR da `trilha/antifraude` · 25/10/2026.

Não confundir com a [ADR-006 herdada da AED](../heranca-aed/adr-aed/ADR-006-resiliencia.md),
que trata de retentativa e DLQ. Esta usa aquela como está e não a repete.

## Contexto

- **Entre serviços de domínio não há chamada síncrona** ([plano §3.2](../plano-de-trabalho.md)).
  São quatro serviços em dois runtimes, e nenhum deles conhece o endereço de outro. Falta dizer
  por onde os fatos passam.
- **O mesmo fato tem mais de um interessado.** `risco.elegibilidade.v1` é lido pela projeção do
  crédito e pelo antifraude; `credito.solicitacao.v1`, pelo risco duas vezes: a análise e o
  agregador de volume. Quem publica não deveria saber quantos leem.
- **O domínio só exige uma ordem: a de cada solicitação.** Dentro de uma solicitação, a
  elegibilidade vem antes da análise antifraude. Entre solicitações diferentes, nada depende de nada.
- **Os interessados andam em ritmos diferentes.** A projeção do crédito é leve; o antifraude grava,
  pontua e publica; a contratação chama o core bancário. E qualquer um deles pode estar fora do
  ar quando o fato acontece.
- **A herança já é Kafka**, com o envelope CloudEvents e a chave `solicitacaoId`
  ([ADR-003 da AED](../heranca-aed/adr-aed/ADR-003-chave-de-particao.md)). Volume do recorte:
  ~1.000 solicitações por dia.

## Decisão

**O Kafka é o único caminho entre serviços de domínio, aplicado como Observer:** cada fato é um
tópico (`<contexto>.<fato>.v1`), cada serviço interessado se inscreve com um consumer group
próprio, e quem publica não sabe quem lê. Três partições por tópico, chave `solicitacaoId` em
todos, entrega *at-least-once* com deduplicação pelo `ce_id` e consistência eventual entre os
lados.

### O Observer mapeado

| Observer (GoF) | No Kafka | No sistema |
|---|---|---|
| *Subject* | tópico | `risco.elegibilidade.v1` |
| `attach(observer)` | um consumer group se inscreve | `antifraude-analise-v1`, `credito-proposta-view-v1` |
| `notify()` | uma única publicação | o risco publica o parecer uma vez, sem lista de destinatários |
| `update()` | `poll` e processamento | `ConsumidorDeElegibilidade` no antifraude |
| `detach(observer)` | o grupo para de consumir | nada muda no produtor |

Duas diferenças contam a favor. A notificação é **persistente**: um observador fora do ar não
perde o fato, e o lê do offset onde parou. E cada observador tem o **próprio ritmo**: o atraso de
um vira *lag* do grupo dele, sem segurar os outros. A contrapartida: a notificação é assíncrona, e
o *Subject* nunca sabe se alguém já reagiu.

### Partições e chave

- **Chave `solicitacaoId` em todos os tópicos.** Todos os eventos de uma solicitação caem na mesma
  partição, e a ordem que o domínio exige vem de graça. Os produtores Python usam o particionador
  `murmur2_random`, o mesmo do cliente Java: a mesma chave cai na **mesma partição em todos os
  tópicos**, seja quem for o produtor. Na demo de 25/10, uma solicitação passou por
  `credito.solicitacao.solicitada.v1`, `risco.elegibilidade.v1` e `antifraude.analise.v1`, todos na
  partição 1.
- **Três partições.** É o teto de paralelismo de cada grupo: três instâncias consomem, a quarta
  fica ociosa. Folga de sobra para ~1.000 solicitações por dia.
- **Aumentar partições quebra a ordem.** A chave passa a cair em outra partição, e uma
  solicitação em curso pode ter eventos nas duas. Só com os tópicos drenados.

### Consumer groups

Padrão `<servico>-<proposito>-v1`. Um grupo por propósito, não por serviço: o risco lê a mesma
solicitação com dois grupos porque são duas reações independentes.

| Tópico | Consumer group | Serviço |
|---|---|---|
| `credito.solicitacao.v1` | `risco-credito-v1` · `risco-fluxo-creditos-v1` | risco: análise · volume por janela |
| `risco.elegibilidade.v1` | `credito-proposta-view-v1` · `antifraude-analise-v1` | crédito: projeção · antifraude |
| `antifraude.analise.v1` | `credito-proposta-view-v1` | crédito: projeção |
| `credito.proposta.v1` | `contratacao-proposta-v1` | contratação |
| `contratacao.contrato.v1` | `credito-proposta-view-v1` | crédito: projeção |

A lista completa, com as DLQs, está no [`asyncapi.yaml`](../contratos/asyncapi.yaml).

### Consistência eventual

A `credito.proposta_view` ([ADR-005](ADR-005-cqrs-lite-vs-completo.md)) é montada por pedaços:
primeiro a elegibilidade, depois a análise antifraude, depois o contrato. Entre um e outro, a
proposta existe com campos vazios. Nas medições de 25/10, a análise antifraude foi publicada
**entre 0,1 s e 1,2 s** depois da solicitação (o maior valor com os consumidores recém-iniciados).
Com um serviço parado, o pedaço dele espera o serviço voltar.

Quem lê convive com isso de três formas:

- o BFF trata a falta do bloco como resposta parcial, não como erro ([ADR-008](ADR-008-bff-agregacao-resiliencia-versionamento.md));
- a projeção não deixa um evento mais antigo sobrescrever um mais novo (compara a `dataAnalise`);
- e deduplica por `(tópico, eventoId)`, porque a entrega é *at-least-once*.

### Decisões do antifraude

- **Elegibilidade recusada encerra o fluxo.** O antifraude não analisa o que o risco recusou: não
  grava, não publica, e a consulta responde 404. Analisar fraude de uma proposta que não vai
  existir custa e não decide nada; a projeção já sabe que ela não é elegível.
- **Sem `antifraude.fraude.v1`.** O `aprovada: false` do `antifraude.analise.v1` já é o fato.
  Um segundo tópico partiria uma decisão em dois eventos, e entre tópicos não há ordem.
- **Falha segue a ADR herdada**, em Python: 4 retentativas bloqueantes (0,5 · 1 · 2 · 4 s), depois
  `risco.elegibilidade.v1.dlq` com a `classificacao` e os cabeçalhos `kafka_dlt-*` no formato do
  Spring. O inválido vai direto, como `PERMANENTE`. Uma DLQ só, lida do mesmo jeito em Java e em
  Python.
- **Idempotência sem outbox.** A análise é gravada pelo `ce_id` de origem e publicada depois. Se a
  publicação falhar, a retentativa encontra a análise gravada e republica o mesmo `eventoId`, e o
  crédito descarta a cópia. Repetir é seguro, então o outbox não se paga aqui.
- **O trace atravessa o tópico.** O `traceparent` recebido continua no processamento e segue nos
  cabeçalhos do que é publicado ([ADR-007](ADR-007-cloudevents-compatibilidade.md)).

## Alternativas consideradas

**HTTP síncrono entre os serviços, com o crédito orquestrando.** É o caminho mais curto de
escrever e de depurar. Descartada porque é o *distributed monolith*: o crédito precisaria conhecer
o endereço e a disponibilidade de cada serviço, e com o antifraude fora do ar a solicitação não
anda. Com o Kafka, a solicitação anda, e o antifraude processa o lag quando volta.

**RabbitMQ com exchange *fanout*.** Também implementa Observer: uma fila por interessado.
Descartada por dois motivos. A mensagem consumida sai da fila, e não há *replay*: a projeção CQRS
não pode ser reconstruída do início, nem a DLQ republicada contra o histórico. E a ordem por
`solicitacaoId` com mais de um consumidor pede o plugin de *consistent hash*, que o Kafka entrega
de série pela chave.

**Um tópico único `eventos`, com o tipo no `ce_type`.** Uma só configuração e uma só DLQ.
Descartada porque todo consumidor leria tudo e filtraria; o contrato deixaria de ser por tópico, e
uma mudança num evento afetaria quem nem o consome.

**Tabela compartilhada no Postgres como fila (outbox lido por todos).** Dispensa o broker.
Descartada porque cruza o Database per Service ([ADR-002](ADR-002-fronteiras-de-contexto-e-database-per-service.md)):
cada consumidor leria o schema de outro.

## Consequências e custos

**O que melhorou**

- Adicionar um consumidor não toca o produtor. O antifraude entrou lendo `risco.elegibilidade.v1`
  sem nenhuma linha nova no risco; a projeção do crédito já lia o mesmo tópico.
- Serviço parado não perde evento. Com o antifraude fora do ar, os pareceres viram lag de
  `antifraude-analise-v1` e são processados na volta, na ordem.
- Java e Python conversam só pelo contrato. O mesmo envelope e o mesmo particionador nos dois
  lados, validados nos dois sentidos: risco (Java) → antifraude (Python) → projeção do crédito (Java).

**O que piorou**

- **Consistência eventual na tela.** A proposta aparece sem o bloco antifraude até o evento chegar;
  o BFF e a SPA precisam saber mostrar "ainda não analisada".
- **Todo consumidor carrega idempotência.** *At-least-once* significa que a reentrega acontece:
  cada serviço mantém o próprio registro por `ce_id`.
- **A retentativa bloqueia a partição inteira.** Para manter a ordem, um evento com o banco fora do
  ar segura um terço das solicitações por pelo menos 7,5 s, mais os timeouts de conexão.
- **Depurar exige ferramenta.** Um fluxo que passa por três tópicos só se lê no Kafka UI ou no
  trace; um `curl` não mostra mais o caminho.
- **Um broker é um ponto único de falha na demo.** O compose roda um nó, com fator de replicação 1.
  Produção pediria três.

## Quando não vale a pena

- **Quando quem pergunta precisa da resposta para continuar.** A consulta do BFF ao antifraude é
  HTTP de propósito: a tela espera o status agora, não um evento depois.
- **Um produtor e um consumidor do mesmo time, no mesmo deploy.** Uma chamada de função ou uma fila
  em memória resolve, sem broker para operar.
- **Volume baixo e sem necessidade de *replay*.** A ~1.000 solicitações por dia, o throughput do
  Kafka não se paga sozinho. O que o justifica aqui é o *replay*, que reconstrói a projeção e
  republica a DLQ, junto com a ordem por chave e os grupos independentes. Sem essas três
  necessidades, um RabbitMQ ou um outbox com polling seriam mais baratos de operar.
- **Quando o domínio exige ordem global.** O Kafka só ordena dentro da partição; ordem entre todas
  as solicitações forçaria uma partição só e mataria o paralelismo.

## Condição de falha

O grupo reabre esta decisão se, em operação normal:

- o lag de qualquer consumer group passar de **100 mensagens por mais de 5 minutos**: o teto de três
  partições ficou pequeno ou o consumidor ficou lento demais;
- o p95 entre `credito.solicitacao.v1` e `antifraude.analise.v1` passar de **5 s** por uma semana:
  a consistência eventual deixou de ser aceitável para a tela de proposta;
- mais de **1% dos eventos** de um tópico cair na DLQ como `PERMANENTE` numa semana: o contrato
  entre os serviços divergiu, e o barramento está escondendo um erro de integração;
- aparecer **uma chamada síncrona** entre serviços de domínio: a fronteira que o Kafka protege foi
  contornada.
