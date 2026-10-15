# App Cliente — SPA

**Dono: Rafael** · `trilha/seguranca` · porta 5173

Client **público**. Authorization Code + **PKCE (S256)**. Nenhum secret aqui dentro — é
exatamente o antipadrão que a apresentação diz ter evitado.

## O que entrega

- Login no Keycloak via PKCE
- Chamada ao BFF com o token: **200**
- Demonstração de **401** (sem token) e **403** (role `cliente` em rota de `analista`)

## A fazer

- [ ] Fluxo PKCE funcionando — **22/10**
- [ ] Telas de solicitação e consulta de proposta
- [ ] Script `curl` equivalente, como plano B da demo
- [ ] `Dockerfile` multi-stage (build Vite → nginx)
