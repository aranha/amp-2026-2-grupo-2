# App Cliente — SPA

**Dono: Rafael** · `trilha/seguranca` · porta 5173

Client **público**. Authorization Code + **PKCE (S256)**. Nenhum secret aqui dentro — é
exatamente o antipadrão que a apresentação diz ter evitado.

## O que entrega

- Login no Keycloak via PKCE
- Chamada ao BFF com o token: **200**
- Demonstração de **401** (sem token) e **403** (role `cliente` em rota de `analista`)

## Rodar

```powershell
docker compose up -d keycloak   # na raiz do repositório
cd app-cliente
npm install
npm run dev                     # http://localhost:5173
```

Ou pelo Docker (nginx servindo o build), sem Node instalado — não rode junto com `npm run dev`,
os dois usam a porta 5173:

```powershell
docker compose up -d --build app-cliente
```

Usuários: `maria.cliente` / `123` (role `cliente`) · `ana.analista` / `123` (role `analista`).
URLs sobrescrevíveis por `.env`: `VITE_OIDC_AUTHORITY` (padrão `http://localhost:8180/realms/credito`)
e `VITE_BFF_URL` (padrão `http://localhost:8080`).

## Como a segurança está montada

- `src/auth.js` — `oidc-client-ts` com `response_type: 'code'`: gera `code_verifier` e
  `code_challenge` (S256) a cada login e confere o `state` na volta. Só `client_id`, nenhum secret.
- Token em `sessionStorage`: morre com a aba.
- Tela montada com `textContent`, nunca `innerHTML` — nada do token ou do BFF vira HTML (XSS).
- **Sair** encerra a sessão no Keycloak (`signoutRedirect`), não só apaga o token local.

Para ver o PKCE: DevTools → Network → *Preserve log* → Entrar. Em `auth?` aparece o
`code_challenge`; em `token`, o `code_verifier`. `base64url(SHA256(verifier)) == challenge`.

## A fazer

- [x] Fluxo PKCE funcionando — **22/10**
- [x] Telas de solicitação e consulta de proposta (`src/telas/`), seguindo `docs/contratos/openapi-bff.yaml`
  — abas Solicitar, Consultar (com comparação v1 × v2), Análises (demo 200/401/403) e Meu token
- [ ] Script `curl` equivalente, como plano B da demo
- [x] `Dockerfile` multi-stage (build Vite → nginx), com CSP e `Referrer-Policy: no-referrer`
