# C4 — Nível 2: Containers

**Responsável:** Paulo Aranha · **Trilha:** `trilha/bff` · Checklist **B8**

## Escopo

As unidades que sobem separadas — cada uma é um container no `docker-compose.yml` e um
Deployment em `infra/k8s/` —, com runtime, porta e por que existem.

![Containers](n2-container.png)

## Os dois eixos que o desenho mostra

**Comunicação.** Síncrono só na borda: `app-cliente → bff → serviços`. Entre serviços de
domínio não há uma seta sequer de HTTP — só Kafka. É assim que o *distributed monolith* fica
fora: nenhum serviço de domínio conhece o endereço de outro.

**Dados.** Um PostgreSQL, quatro schemas, um usuário por schema com `GRANT` restrito. Nenhuma
seta de serviço para schema alheio, e um `SELECT` cruzando fronteira falha com
`permission denied`. Decisão em [ADR-002](../adr/ADR-002-fronteiras-de-contexto-e-database-per-service.md).

## Por que o BFF não é repasse

Ele agrega **três** fontes num `GET /v2/propostas/{id}`, degrada em vez de falhar quando risco
ou antifraude não respondem, e mantém `/v1` e `/v2` vivas ao mesmo tempo. As três coisas estão
na [ADR-008](../adr/ADR-008-bff-agregacao-resiliencia-versionamento.md).

## Observabilidade

Os cinco serviços exportam OTLP para o Collector, que distribui para Tempo, Prometheus e Loki,
lidos no Grafana. O `traceparent` viaja no envelope CloudEvents, então o trace atravessa o
Kafka. Decisão em [ADR-010](../adr/ADR-010-opentelemetry-e-loki.md).

## Fonte

`n2-container.drawio` é o editável (diagrams.net). O PNG sai do Mermaid abaixo:

```mermaid
flowchart TB
    cliente(["<b>Cliente</b> · <b>Analista</b><br/><i>pessoas</i>"])
    keycloak["<b>Keycloak</b> :8180<br/><i>IdP externo</i>"]
    core["<b>Core Bancário</b><br/><i>mock, M2M</i>"]

    subgraph borda["Borda"]
        spa["<b>app-cliente</b> :5173<br/><i>SPA — JS puro + Vite, nginx</i><br/>PKCE S256, sem secret"]
        bff["<b>bff</b> :8080<br/><i>Java 21 / Spring Boot</i><br/>Agrega 3 fontes · Resilience4j · v1 e v2"]
    end

    subgraph dominio["Serviços de domínio"]
        credito["<b>servico-credito</b> :8082<br/><i>Java 21 / Spring Boot</i><br/>Agregado, CQRS, proposta_view"]
        risco["<b>servico-risco</b> :8083<br/><i>Java 21 / Spring Boot</i><br/>Hexagonal, elegibilidade"]
        antifraude["<b>servico-antifraude</b> :8084<br/><i>Python 3.12 / FastAPI</i><br/>Score de fraude"]
        contratacao["<b>servico-contratacao</b> :8085<br/><i>Python 3.12 / FastAPI</i><br/>Contrato e liberação"]
    end

    kafka[("<b>Kafka</b> :19092<br/><i>6 tópicos, CloudEvents 1.0</i>")]
    postgres[("<b>PostgreSQL</b> :15432<br/><i>4 schemas, 1 usuário cada</i>")]

    subgraph obs["Observabilidade"]
        otel["<b>OTel Collector</b><br/>:4317 / :4318"]
        grafana["<b>Grafana</b> :3000<br/>Tempo · Prometheus · Loki"]
    end

    cliente -->|"HTTPS"| spa
    spa -.->|"login PKCE"| keycloak
    spa -->|"JSON + Bearer JWT"| bff
    bff -.->|"JWKS"| keycloak

    bff -->|"GET /v1/propostas/id<br/>POST /solicitacoes"| credito
    bff -->|"GET /v1/analises/id"| risco
    bff -->|"GET /v1/analises/id"| antifraude

    credito <-->|"solicitacao · proposta"| kafka
    risco <-->|"elegibilidade"| kafka
    antifraude <-->|"analise"| kafka
    contratacao <-->|"contrato · liberacao"| kafka

    credito --> postgres
    risco --> postgres
    antifraude --> postgres
    contratacao --> postgres

    contratacao -->|"client credentials"| core

    bff -.-> otel
    credito -.-> otel
    risco -.-> otel
    antifraude -.-> otel
    contratacao -.-> otel
    otel --> grafana

    classDef pessoa fill:#fff0d9,stroke:#536579,color:#1a2027
    classDef nosso fill:#dceafe,stroke:#536579,color:#1a2027
    classDef externo fill:#e9e5ff,stroke:#536579,color:#1a2027
    classDef dados fill:#e9f7ec,stroke:#536579,color:#1a2027
    classDef observ fill:#e7f4fa,stroke:#536579,color:#1a2027
    class cliente pessoa
    class spa,bff,credito,risco,antifraude,contratacao nosso
    class keycloak,core externo
    class kafka,postgres dados
    class otel,grafana observ
```
