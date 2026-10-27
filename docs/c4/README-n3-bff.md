# C4 — Nível 3: Componentes do BFF

**Responsável:** Paulo Aranha · **Trilha:** `trilha/bff` · Checklist **B9**

## Escopo

O interior do `bff`: o que roda antes do controller, quem orquestra, quem fala com fora e o que
cada peça faz quando a de fora não responde.

![Componentes do BFF](n3-componente-bff.png)

## O que o desenho defende

**A segurança não é um componente entre outros — ela é a porta.** A cadeia do `SecurityConfig`
roda antes de qualquer controller existir no caminho. Token ausente ou inválido nunca chega na
entrada HTTP: vira 401 no filtro. Token válido sem a role vira 403 na regra da rota. É por isso
que os dois códigos saem de lugares diferentes do diagrama.

**O agregador é o único que sabe compor.** Os controllers não chamam downstream: o
`AgregadorDePropostas` chama o crédito primeiro — sem proposta, 404 sem gastar as outras duas
chamadas — e dispara risco e antifraude em paralelo, em threads virtuais. O
`PropostaV1Controller` não tem caminho próprio: ele projeta o mesmo objeto da v2 na forma plana.
Uma agregação, duas versões.

**Cada client carrega a própria política de falha, e elas não são iguais.** É a parte do desenho
que mais diz sobre a arquitetura:

| Client | Política | Por quê |
|---|---|---|
| `CreditoClient` | retry · breaker · bulkhead, **sem fallback** | sem proposta não há resposta parcial possível; vira 503 |
| `RiscoClient` | retry · breaker · bulkhead, fallback | degrada para `parecerIndisponivel` e o bloco some |
| `AntifraudeClient` | retry · breaker · bulkhead, fallback | degrada para `antifraudeIndisponivel` |
| `AnalisesClient` | retry · breaker · bulkhead | leitura do risco para a rota de analista |
| `SolicitacaoClient` | breaker · bulkhead, **sem retry** | criar solicitação não é idempotente: repetir geraria duplicata |

O `SolicitacaoClient` é o único sem retry, e isso é deliberado — há teste que falha se alguém
acrescentar a anotação. Decisões e custos na [ADR-008](../adr/ADR-008-bff-agregacao-resiliencia-versionamento.md).

**Os erros saem por um lugar só.** `PropostaNaoEncontrada`, `FonteIndisponivel` e
`SolicitacaoInvalida` sobem até o `TratadorDeErros`, que devolve `application/problem+json`. Os
dois primeiros também decidem o que **não** é falha: 404 e 400 do downstream ficam fora do
circuit breaker, porque recusa é resposta, não indisponibilidade.

## Fonte

`n3-componente-bff.drawio` é o editável (diagrams.net). O PNG sai do Mermaid abaixo:

```mermaid
flowchart TB
    spa(["<b>app-cliente</b><br/><i>SPA</i> — JSON + Bearer JWT"])
    keycloak[["<b>Keycloak</b> · JWKS"]]

    subgraph seg["Cadeia de segurança — roda antes de qualquer controller"]
        sec["<b>SecurityConfig</b><br/>CORS · filter chain · JwtDecoder RS256 por JWKS"]
        aud["<b>ValidadorDeAudience</b><br/>aud = bff-api"]
        roles["<b>ConversorDeRolesDoRealm</b><br/>realm_access.roles → ROLE_*"]
    end

    subgraph entrada["Entrada HTTP"]
        c2["<b>PropostaV2Controller</b><br/>GET /v2/propostas/{id}"]
        c1["<b>PropostaV1Controller</b><br/>GET /v1/propostas/{id}<br/>Deprecation · Sunset · Link"]
        cs["<b>SolicitacaoController</b><br/>POST /v1/solicitacoes<br/>clienteId sai do token"]
        ca["<b>AnalisesController</b><br/>GET /v1/analises<br/>exige a role analista"]
    end

    agg["<b>AgregadorDePropostas</b><br/>crédito primeiro — sem proposta, 404<br/>risco e antifraude em paralelo, threads virtuais"]
    err["<b>TratadorDeErros</b><br/>400 · 404 · 503 em problem+json"]

    subgraph saida["Saída HTTP — Resilience4j, uma instância por downstream"]
        cfg["<b>ClientesHttpConfig</b><br/>um RestClient por downstream<br/>2s conexão / 3s leitura · executor virtual"]
        cc["<b>CreditoClient</b><br/>retry · breaker · bulkhead<br/>sem fallback: vira 503"]
        rc["<b>RiscoClient</b><br/>retry · breaker · bulkhead<br/>fallback → parecerIndisponivel"]
        ac["<b>AntifraudeClient</b><br/>retry · breaker · bulkhead<br/>fallback → antifraudeIndisponivel"]
        nc["<b>AnalisesClient</b><br/>retry · breaker · bulkhead"]
        sc["<b>SolicitacaoClient</b><br/>breaker · bulkhead<br/><b>sem retry</b> — POST não é idempotente"]
    end

    credito[["<b>servico-credito</b>"]]
    risco[["<b>servico-risco</b>"]]
    antifraude[["<b>servico-antifraude</b>"]]

    spa --> sec
    sec -.->|"busca as chaves"| keycloak
    sec --> aud
    sec --> roles
    sec --> c2
    sec --> c1
    sec --> cs
    sec --> ca

    c2 --> agg
    c1 -->|"projeta a v2 na forma plana"| agg
    agg --> cc
    agg --> rc
    agg --> ac
    ca --> nc
    cs --> sc

    cfg -.->|"injeta em todos"| cc
    cc --> credito
    sc --> credito
    rc --> risco
    nc --> risco
    ac --> antifraude

    agg -.->|"PropostaNaoEncontrada"| err
    cc -.->|"FonteIndisponivel"| err
    sc -.->|"SolicitacaoInvalida"| err

    classDef externo fill:#e9e5ff,stroke:#536579,color:#1a2027
    classDef nosso fill:#dceafe,stroke:#536579,color:#1a2027
    classDef infra fill:#e7f4fa,stroke:#536579,color:#1a2027
    classDef pessoa fill:#fff0d9,stroke:#536579,color:#1a2027
    class spa pessoa
    class sec,aud,roles,c1,c2,cs,ca,err,agg,cc,rc,ac,nc,sc nosso
    class cfg infra
    class keycloak,credito,risco,antifraude externo
```
