# ADR-010 — OpenTelemetry e Loki em vez de ELK

## Status

Aceito. `trilha/plataforma` (Sesaque).

## Contexto

- **Uma solicitação passa por quatro processos e três tópicos.** O `POST /v1/solicitacoes` entra no
  BFF, vira HTTP para o crédito, evento para o risco, evento para o antifraude e evento de volta
  para o read model do crédito. Sem correlação, cada serviço só vê o próprio pedaço, e "por que
  essa proposta demorou?" vira uma busca manual em cinco logs.
- **Dois runtimes.** Três serviços em Java/Spring e dois em Python/FastAPI (ADR-003). A
  instrumentação precisa falar o mesmo protocolo e o mesmo formato de contexto nos dois.
- **O Kafka quebra a propagação.** No HTTP, o `traceparent` W3C vai no cabeçalho por padrão. No
  Kafka, alguém precisa colocá-lo nos cabeçalhos da mensagem e tirá-lo do outro lado — e o
  `confluent-kafka` do Python não tem instrumentação automática que faça isso.
- **O enunciado cobra os três sinais**, o trace atravessando a mensageria e dashboards de latência,
  disponibilidade, banco, fila e cluster (B48–B56, D8).
- **Volume pequeno, máquinas pequenas.** ~1.000 solicitações por dia (estimativa do domínio) e
  notebooks de 8 GB que já rodam Kafka, Postgres, Keycloak e cinco serviços.

## Decisão

**OpenTelemetry nos cinco serviços, um Collector como ponto único de coleta, e um backend por
sinal: Tempo para traces, Prometheus para métricas, Loki para logs, Grafana por cima.**

```
 bff · crédito · risco ──(agente Java)──┐
                                        ├─ OTLP gRPC ─► Collector ─┬─► Tempo ──(metrics-generator)─► Prometheus
 antifraude · contratação ─(instrument)─┘                          ├─► Prometheus (:8889)
                                                                   └─► Loki (OTLP)
 actuator · kafka-exporter · postgres-exporter ◄── scrape ── Prometheus          Grafana lê os três
```

| Peça | Como | Por quê |
|---|---|---|
| **Java** | `opentelemetry-javaagent` 2.11 na imagem, por `JAVA_TOOL_OPTIONS`, versão e checksum fixos no Dockerfile | instrumenta Spring MVC, RestClient, JDBC, Hikari e Kafka **sem tocar no código**; injeta e extrai o `traceparent` nos cabeçalhos do Kafka sozinho |
| **Python** | `opentelemetry-instrument` + FastAPI, psycopg e métricas de processo | mesma auto-instrumentação; no Kafka o `traceparent` é propagado à mão (`propagate.inject` / `extract`), ~10 linhas por serviço, com teste |
| **Collector** | `memory_limiter` → filtro de health check → `batch`, uma pipeline por sinal | os serviços só conhecem `otel-collector:4317`; trocar backend é mudar um arquivo |
| **Tempo** | traces + *metrics-generator* (service graph e span metrics) | o mapa de serviços do Grafana e a latência por span, inclusive das queries, com a **mesma métrica** para Java e Python |
| **Prometheus** | métricas OTLP via Collector, mais scrape do actuator, `kafka-exporter` e `postgres-exporter` | lag por consumer group e o Postgres visto de dentro não saem dos serviços |
| **Loki** | logs por OTLP; `service_name` vira label, `trace_id` vira metadado estruturado | do log para o trace e do trace para o log, nos dois sentidos |

O resultado, medido no compose: **um único trace de 24 spans** vai do `POST /v1/solicitacoes` no
BFF ao crédito, atravessa `credito.solicitacao.solicitada.v1` até o risco,
`risco.elegibilidade.v1` até o antifraude (Python) e `antifraude.analise.v1` de volta ao crédito —
três saltos de fila, quatro serviços, dois runtimes.

### Loki e não ELK

O Loki indexa **só os labels** (`service_name`, nível) e guarda a linha comprimida em blocos. O
Elasticsearch indexa **cada palavra** de cada linha. Para a pergunta que fazemos — "os logs deste
serviço, deste trace" — o índice de labels basta, e o custo é uma fração: medido no compose, o Loki roda com
~65 MB de memória; um Elasticsearch de nó único é configurado com pelo menos 1 GB de heap antes
de receber a primeira linha. E o
Loki fala OTLP nativamente, sem Logstash nem Filebeat no meio.

## Alternativas consideradas

**ELK (Elasticsearch, Logstash, Kibana).** Busca full-text arbitrária e agregações ricas sobre o
conteúdo do log. Descartado pelo custo de memória numa stack que já está no limite dos notebooks, e
porque o Kibana não correlaciona com traces e métricas na mesma tela, como o Grafana faz com Tempo
e Prometheus.

**Micrometer + Brave/Zipkin nos Java, SDK manual no Python.** Instrumentação nativa do Spring, sem
agente. Descartado: são dois modelos de instrumentação, exige dependência e configuração em cada
serviço, e o formato de propagação padrão do Brave (B3) precisaria ser convertido para W3C para
conversar com o Python.

**Jaeger no lugar do Tempo.** Interface própria madura. Descartado porque o Tempo guarda trace em
disco local sem índice (barato) e gera service graph e span metrics no próprio processo, o que
alimenta os dashboards sem outro componente.

**Exportar direto dos serviços para cada backend, sem Collector.** Um container a menos.
Descartado: cada serviço passaria a conhecer três endereços e três protocolos, e o filtro de health
check e o `memory_limiter` teriam de existir em cinco lugares.

**Instrumentação manual em todos os serviços.** Controle total dos spans. Descartada: o agente e o
`opentelemetry-instrument` já cobrem HTTP, banco e Kafka; escrever isso à mão é código de
infraestrutura dentro do domínio. A exceção consciente é a propagação no `confluent-kafka`.

## Consequências e custos

**Melhorou**

- "Por que essa proposta demorou?" se responde abrindo **um** trace, com o tempo de cada salto de
  fila e de cada query.
- Log e trace se encontram: do span para as linhas daquele trace no Loki, e de uma linha de log
  para o trace pelo `trace_id`.
- Os serviços Java não têm uma linha de código de observabilidade; os Python, só a propagação no
  Kafka.
- Lag de consumer group e profundidade da DLQ ficam visíveis antes de virarem reclamação.

**Piorou**

- **Mais sete containers** no compose (Collector, Tempo, Loki, Prometheus, Grafana e dois
  exporters). Somam ~520 MB de memória, medidos com a demo rodando.
- **O agente Java soma 22 MB à imagem e alguns segundos ao boot**, e instrumenta tudo que reconhece
  — inclusive o que não interessa. O health check virou metade dos spans até entrar o filtro no
  Collector.
- **A propagação manual no Python é código nosso para manter.** Se alguém publicar no Kafka por
  outro caminho, o trace quebra ali, em silêncio. O `test_rastreamento.py` e o `test_consumo.py`
  seguram isso.
- **Métricas duplicadas nos Java.** JVM e Hikari chegam pelo agente (OTLP) e pelo actuator
  (Micrometer). Cada dashboard usa uma fonte só, de propósito.
- **Retenção curta.** Tempo guarda 1 h e Loki e Prometheus o padrão local: serve para demonstrar e
  depurar, não para auditoria.

## Quando não vale a pena

- **Um serviço só, ou um monolito.** Trace distribuído sem distribuição é custo sem pergunta para
  responder; log estruturado e métricas do framework bastam.
- **Busca full-text em log histórico** — investigação de segurança, auditoria, "em que dia apareceu
  esta mensagem?". Aí o índice completo do ELK ganha do índice de labels do Loki.
- **Volume alto sem amostragem.** Com milhares de requisições por segundo, guardar 100% dos traces
  custa mais que o sistema observado; é preciso *tail sampling* no Collector antes de ligar isto.
- **Equipe sem tempo de olhar.** Dashboard que ninguém abre é a "camada decorativa" de que o
  professor fala: o custo é o mesmo, o retorno é zero.

## Condição de falha

Reabrimos esta decisão se:

- o trace de uma solicitação **deixar de atravessar** os três tópicos (span órfão no consumidor)
  depois de uma mudança de biblioteca, por mais de uma sprint;
- o overhead do agente passar de **10% na latência p95** do BFF, comparado com a imagem sem ele;
- a stack de observabilidade passar de **1,5 GB** de memória no compose, o que inviabiliza os
  notebooks de 8 GB do grupo;
- precisarmos de busca full-text em log com mais de **7 dias** — sinal de que o problema virou
  auditoria, e aí ELK ou um Loki com índice de bloom entram na conversa.
