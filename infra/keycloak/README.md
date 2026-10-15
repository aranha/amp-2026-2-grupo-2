# Keycloak

Dono: **Rafael** · realm `credito` · http://localhost:8180 (`admin` / `admin`)

O arquivo `realm-credito.json` desta pasta é importado no boot (`--import-realm`).
**Exportar até 18/10** — Paulo e Marcus dependem dele.

## Clients

| Client | Tipo | Fluxo | Para quê |
|---|---|---|---|
| `app-cliente` | público | Authorization Code + **PKCE (S256)** | SPA. **Sem secret** — é o antipadrão evitado |
| `bff` | confidencial | — | Valida o token, audience `bff-api` |
| `servico-contratacao-m2m` | confidencial | **Client Credentials** | Serviço → core bancário |

## Roles

`cliente` · `analista` · `operador`

`GET /v1/analises` exige `analista` — é o endpoint da demo de **403**.

## Exportar o realm

```bash
docker compose exec keycloak /opt/keycloak/bin/kc.sh export \
  --dir /tmp/export --realm credito --users realm_file
docker compose cp keycloak:/tmp/export/credito-realm.json \
  ./infra/keycloak/realm-credito.json
```
