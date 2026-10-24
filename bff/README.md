# BFF — Java 21 / Spring Boot

**Dono: Paulo Aranha** · `trilha/bff` · porta 8080

Camada de borda. Única porta de entrada do `app-cliente`.

## O que entrega

- `GET /v2/propostas/{id}` agregando **três** fontes: proposta (crédito), parecer (risco),
  status (antifraude). Não é proxy — se fosse repasse 1:1, seria camada decorativa
- `GET /v1/propostas/{id}` com os campos planos, header `Deprecation` + `Sunset`
- Resilience4j por downstream: circuit breaker, **retry só em GET**, timeout, bulkhead, fallback
- Spring Security Resource Server validando o JWT *(config com Rafael)*

## Como rodar

```bash
mvn -f bff/pom.xml spring-boot:run   # http://localhost:8080/actuator/health
mvn -f bff/pom.xml test              # o mesmo que o job `Teste · Java (bff)` do CI roda
```

## Dependências

Spring Web · Spring Security OAuth2 Resource Server · Resilience4j · springdoc-openapi ·
Micrometer + OTel

## A fazer

- [x] Esqueleto respondendo `/actuator/health` — **16/10**
- [ ] Contrato REST `/v1` e `/v2` publicado para o Rafael — **20/10**
- [ ] Agregação das 3 fontes
- [ ] Resilience4j completo, com fallback degradado
- [x] `Dockerfile` multi-stage *(escrito; build ainda não validado — Docker parado)*
- [ ] `docs/contratos/openapi-bff.yaml`
- [ ] ADR-001, ADR-003, ADR-008
