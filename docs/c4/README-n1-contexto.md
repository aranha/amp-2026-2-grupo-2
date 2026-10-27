# C4 — Nível 1: Contexto

**Responsável:** Paulo Aranha · **Trilha:** `trilha/bff` · Checklist **B7**

## Escopo

Quem usa a plataforma e com que sistemas de fora ela fala. Nada de tecnologia interna: neste
nível o sistema é uma caixa só.

![Contexto](n1-contexto.png)

## Quem aparece, e por quê

- **Cliente** — pede crédito e acompanha a proposta pelo navegador. Autentica no Keycloak por
  Authorization Code + PKCE; a aplicação nunca vê a senha.
- **Analista** — revisa pareceres de risco. É a mesma porta de entrada, com a role `analista`;
  é essa diferença que a demo mostra como **403**.
- **Keycloak** — Identity Provider OAuth2/OIDC. Externo porque não é código nosso e porque
  troca-lo não muda o domínio. Decisão em [ADR-009](../adr/ADR-009-keycloak-oauth2-oidc-pkce.md).
- **Core Bancário** — quem efetivamente libera o dinheiro. No trabalho é um mock, chamado por
  client credentials a partir do `servico-contratacao`.

## O que **não** aparece, de propósito

Antifraude e análise de risco são **contextos internos**, não sistemas de terceiros: estão dentro
da caixa e aparecem no nível 2. Não há bureau de crédito externo no que foi construído — o score
de fraude é calculado pelo nosso próprio serviço.

## Fonte

`n1-contexto.drawio` é o editável (diagrams.net). O PNG sai do Mermaid abaixo:

```mermaid
flowchart TB
    cliente(["<b>Cliente</b><br/><i>pessoa</i><br/>pede crédito e acompanha a proposta"])
    analista(["<b>Analista</b><br/><i>pessoa</i><br/>revisa os pareceres de risco"])

    sistema["<b>Plataforma de Concessão de Crédito</b><br/><i>sistema</i><br/>Recebe a solicitação, avalia risco e fraude,<br/>emite a proposta e formaliza o contrato"]

    keycloak["<b>Keycloak</b><br/><i>sistema externo</i><br/>Identity Provider OAuth2 / OIDC"]
    core["<b>Core Bancário</b><br/><i>sistema externo</i><br/>Liberação do valor ao cliente"]

    cliente -->|"solicita crédito e consulta a proposta<br/>HTTPS"| sistema
    analista -->|"consulta pareceres<br/>HTTPS, exige a role analista"| sistema
    cliente -.->|"autentica<br/>Authorization Code + PKCE"| keycloak
    analista -.->|"autentica"| keycloak
    sistema -->|"valida o token pelo JWKS<br/>RS256, aud bff-api"| keycloak
    sistema -->|"manda liberar o valor<br/>REST, client credentials"| core

    classDef pessoa fill:#fff0d9,stroke:#536579,color:#1a2027
    classDef nosso fill:#dceafe,stroke:#536579,color:#1a2027
    classDef externo fill:#e9e5ff,stroke:#536579,color:#1a2027
    class cliente,analista pessoa
    class sistema nosso
    class keycloak,core externo
```
