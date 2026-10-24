# Keycloak

Dono: **Rafael** · realm `credito` · http://localhost:8180 (`admin` / `admin`)

O arquivo `realm-credito.json` desta pasta é importado no boot (`--import-realm`): quem rodar
`docker compose up -d keycloak` já recebe o realm configurado, sem clicar em nada.

> O import só acontece se o realm **ainda não existir**. Se o seu Keycloak já tem um `credito`
> antigo, recrie o container: `docker compose rm -sf keycloak && docker compose up -d keycloak`.

## Endpoints

O navegador enxerga o Keycloak em `localhost:8180`; um container, em `keycloak:8080`. Com
`KC_HOSTNAME` + `KC_HOSTNAME_BACKCHANNEL_DYNAMIC` no compose, **o `iss` é sempre o mesmo**, venha o
pedido de onde vier, e cada um chama token e JWKS pelo endereço que alcança.

| Para quê | Do navegador / host | De dentro do Docker |
|---|---|---|
| Issuer (`iss`) — valor a conferir | `http://localhost:8180/realms/credito` | **o mesmo** `http://localhost:8180/realms/credito` |
| Token | `http://localhost:8180/realms/credito/protocol/openid-connect/token` | `http://keycloak:8080/realms/credito/protocol/openid-connect/token` |
| JWKS (chaves públicas) | `http://localhost:8180/realms/credito/protocol/openid-connect/certs` | `http://keycloak:8080/realms/credito/protocol/openid-connect/certs` |
| Discovery (OIDC) | `http://localhost:8180/realms/credito/.well-known/openid-configuration` | `http://keycloak:8080/realms/credito/.well-known/openid-configuration` |

**Spring Boot (BFF), rodando no compose:** configure as duas propriedades. Com `jwk-set-uri`
presente, o Spring busca as chaves pela rede interna e usa `issuer-uri` só para conferir o `iss`,
sem tentar acessar `localhost:8180` de dentro do container.

```yaml
spring.security.oauth2.resourceserver.jwt:
  jwk-set-uri: http://keycloak:8080/realms/credito/protocol/openid-connect/certs
  issuer-uri: http://localhost:8180/realms/credito
```

## Clients

| Client | Tipo | Fluxo | Para quê |
|---|---|---|---|
| `app-cliente` | público | Authorization Code + **PKCE (S256)** | SPA. **Sem secret** — é o antipadrão evitado |
| `bff` | confidencial | — | Representa a API. Tokens do `app-cliente` saem com `aud = bff-api` |
| `servico-contratacao-m2m` | confidencial | **Client Credentials** | Serviço → core bancário |

- `app-cliente` tem *Direct access grants* (password grant) **desligado** — outro antipadrão evitado.
- A audience `bff-api` vem do client scope `bff-api` (mapper `audience-bff-api`), atribuído como
  *Default* só ao `app-cliente`. Token do M2M **não** carrega `bff-api` — o BFF responde 401.
- Access token vale **5 minutos** (`accessTokenLifespan = 300`).
- Os secrets dos clients confidenciais estão no JSON (campo `secret`). São de desenvolvimento local;
  em produção viriam de um cofre / `Secret` do Kubernetes, nunca do repositório.

## Roles

`cliente` · `analista` · `operador` (realm roles, chegam no token em `realm_access.roles`)

`GET /v1/analises` exige `analista` — é o endpoint da demo de **403**.

## Usuários de teste

| Usuário | Senha | Role | Uso na demo |
|---|---|---|---|
| `maria.cliente` | `123` | `cliente` | **403** em `GET /v1/analises` |
| `ana.analista` | `123` | `analista` | **200** em `GET /v1/analises` |

## Testar: token M2M

```powershell
$resp = Invoke-RestMethod -Method Post `
  -Uri "http://localhost:8180/realms/credito/protocol/openid-connect/token" `
  -Body @{ grant_type="client_credentials"; client_id="servico-contratacao-m2m"; client_secret="<secret do JSON>" }
$resp.access_token
```

Para ver o token de um usuário sem tela: **Clients → app-cliente → Client scopes → Evaluate**,
escolher o usuário e abrir **Generated access token**.

## Exportar o realm

Depois de mudar algo no painel, exporte de novo e faça commit do JSON.

O `kc.sh export` **não roda com o Keycloak ligado**: em `start-dev` o banco é um H2 em arquivo,
e o servidor já mantém o arquivo travado (`The file is locked`). Por isso o export é feito a
partir de uma cópia do container parado:

```powershell
docker compose stop keycloak
docker commit amp-keycloak kc-export-tmp
docker run --name kc-export --entrypoint /opt/keycloak/bin/kc.sh kc-export-tmp `
  export --dir /tmp/export --realm credito --users realm_file
docker cp kc-export:/tmp/export/credito-realm.json ./infra/keycloak/realm-credito.json
docker rm kc-export
docker rmi kc-export-tmp
docker compose start keycloak
```

> ⚠️ O Keycloak **não tem volume**: o que foi feito no painel existe só dentro do container.
> `stop`/`start` são seguros; `down` ou `rm` **apagam** tudo o que não foi exportado.
