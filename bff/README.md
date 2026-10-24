# BFF — Java 21 / Spring Boot

**Dono: Paulo Aranha** · `trilha/bff` · porta 8080

Camada de borda. Única porta de entrada do `app-cliente`.

## O que entrega

- `GET /v2/propostas/{id}` agregando **três** fontes: proposta (crédito), parecer (risco),
  status (antifraude). Não é proxy — se fosse repasse 1:1, seria camada decorativa
- `GET /v1/propostas/{id}` com os campos planos, header `Deprecation` + `Sunset`
- `POST /v1/solicitacoes` abrindo a solicitação, **sem retry** — criar não é idempotente
- Resilience4j por downstream: circuit breaker, **retry só em GET**, timeout, bulkhead, fallback
- Spring Security Resource Server validando o JWT *(config com Rafael)*

## Como rodar

```bash
mvn -f bff/pom.xml spring-boot:run   # http://localhost:8080/actuator/health
mvn -f bff/pom.xml test              # o mesmo que o job `Teste · Java (bff)` do CI roda
```

## Segurança

Resource Server contra o realm `credito`. O decoder é montado na mão, não por descoberta OIDC:
o BFF sobe com o Keycloak fora do ar (as chaves vêm no primeiro token) e o `iss` do token é o
hostname público, que não é o endereço do JWKS dentro do compose.

| Validação | Onde |
|---|---|
| Assinatura RS256 contra o JWKS | `SecurityConfig#jwtDecoder` |
| `iss` e `exp` | `JwtValidators.createDefaultWithIssuer` |
| `aud = bff-api` | `ValidadorDeAudience` |
| `realm_access.roles` → authorities | `ConversorDeRolesDoRealm` |

`GET /v1/analises` exige a role `analista`. Sem token é **401**; com `maria.cliente` é **403**;
com `ana.analista` é **200**. CORS liberado só para `http://localhost:5173`, a origem da SPA —
sem isso o navegador derruba o preflight e a tela mostra "BFF indisponível" em vez do 401.

Sobrescrevíveis por ambiente: `OIDC_ISSUER`, `OIDC_JWKS`, `OIDC_AUDIENCE`, `CORS_ORIGENS`.

No `docker-compose.yml` os dois primeiros apontam para lugares **diferentes de propósito**:
`OIDC_ISSUER` é `http://localhost:8180`, o hostname público que vem no `iss` do token, e
`OIDC_JWKS` é `http://keycloak:8080`, o endereço interno de onde o container busca as chaves.
Igualar os dois quebra a validação — num sentido ou no outro.

## Agregação e resiliência

`GET /v2/propostas/{id}` chama três fontes: a proposta no crédito, o parecer no risco e o status
no antifraude. O crédito vem primeiro — sem proposta não há o que agregar, e a resposta é 404.
Risco e antifraude saem em paralelo, em threads virtuais.

| Padrão | Configuração | Instância |
|---|---|---|
| Timeout | 2s conexão / 3s leitura | por downstream |
| Circuit breaker | janela 10, 50% de falha, abre 10s, half-open 3 | `credito`, `risco`, `antifraude` |
| Retry | 3 tentativas, backoff exponencial | só nos GET |
| Bulkhead | semáforo de 10 | por downstream |

Fallback: risco fora do ar devolve `parecerIndisponivel: true`, antifraude fora devolve
`antifraudeIndisponivel: true`, e o bloco correspondente some da resposta. O crédito não degrada —
sem ele a resposta é **503** com a fonte no corpo, porque devolver proposta sem proposta seria mentira.

404 do downstream não conta como falha para o circuit breaker: é resposta, não indisponibilidade.

O `POST /v1/solicitacoes` fica de fora do retry por ser a única rota que cria estado: repetir
geraria solicitação duplicada. Continua com circuit breaker e bulkhead, que não repetem nada.
Há teste que falha se alguém acrescentar `@Retry` nele.

Ele também não é repasse: `clienteId` vem do `sub` do token, não do corpo, e `canalOrigem` e
`dataSolicitacao` são do BFF. O `Location` da resposta aponta para a consulta da proposta.

Estado dos breakers em `GET /actuator/circuitbreakers`; métricas em `GET /actuator/prometheus`,
incluindo `resilience4j_circuitbreaker_state` e `http_server_requests_seconds` — é de onde saem
os dashboards de latência e de disponibilidade.

## Versionamento

| Rota | Forma |
|---|---|
| `GET /v1/propostas/{id}` | campos planos: `valorAprovado`, `taxa`, `parcelas`, `status` |
| `GET /v2/propostas/{id}` | `condicoes { valorAprovado, taxa, parcelas, cet }`, sem `taxa` no topo |

Tirar `taxa` do topo é breaking change, por isso a v2. A v1 continua respondendo, com
`Deprecation`, `Sunset` e um `Link` apontando para a sucessora. As duas leem da mesma agregação.

> `taxa`, `parcelas` e `cet` dependem do `servico-credito` expor as condições em
> `GET /v1/propostas/{id}` — hoje o `PropostaView` só tem `valorAprovado`. O BFF mapeia se vier e
> devolve nulo se não vier.

## Dependências

Spring Web · Spring Security OAuth2 Resource Server · Resilience4j · springdoc-openapi ·
Micrometer + OTel *(OTel entra com a trilha de observabilidade)*

## A fazer

- [x] Esqueleto respondendo `/actuator/health` — **16/10**
- [x] Contrato REST `/v1` e `/v2` publicado para o Rafael — **20/10**
- [x] Resource Server, CORS e `GET /v1/analises` com role `analista`
- [x] Agregação das 3 fontes
- [x] Resilience4j completo, com fallback degradado
- [x] `/v1` e `/v2` convivendo, com `Deprecation` e `Sunset`
- [x] `Dockerfile` multi-stage
- [x] `docs/contratos/openapi-bff.yaml`
- [x] ADR-001, ADR-003, ADR-008
- [x] `POST /v1/solicitacoes` sem retry
- [x] `/actuator/prometheus` para os dashboards
