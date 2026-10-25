# ADR-009 — Keycloak, OAuth2/OIDC e PKCE

## Status

Aceito

## Contexto

- **Três tipos de chamador.** Uma pessoa no navegador (o cliente que pede crédito), funcionários
  com permissões diferentes (analista, operador) e um serviço chamando outro sem pessoa envolvida
  (contratação → core bancário).
- **Dois runtimes.** Os serviços estão em Java e em Python. Se cada um cuidasse de login, haveria
  tabela de senhas e lógica de sessão duplicadas em duas linguagens.
- **A SPA roda no navegador do usuário.** Qualquer um lê o JavaScript com F12, então ela não
  consegue guardar segredo nenhum.
- **Autorização por papel.** A mesma rota precisa responder de forma diferente para quem é
  `cliente` e para quem é `analista` — e a demo cobra **200 / 401 / 403**.
- **LGPD.** O domínio lida com CPF e dados financeiros. Um JWT é só *codificado* em base64, não
  criptografado: qualquer pessoa que o capture lê o conteúdo.

## Decisão

Centralizamos identidade num **Identity Provider OIDC — Keycloak 26**, realm `credito` —, e cada
tipo de chamador usa o fluxo OAuth2 feito para ele. O BFF valida o token localmente.

| Chamador | Client | Tipo | Fluxo |
|---|---|---|---|
| Pessoa no navegador | `app-cliente` | público | Authorization Code + **PKCE S256** |
| A API (borda) | `bff` | confidencial | nenhum — só recebe e valida tokens |
| Serviço → serviço | `servico-contratacao-m2m` | confidencial | **Client Credentials** |

**Como o token é validado (BFF, Spring Security Resource Server).** O BFF baixa as chaves
públicas do Keycloak (**JWKS**) e confere, sem chamar o Keycloak a cada requisição:

- assinatura **RS256** — só o Keycloak tem a chave privada, então ninguém forja o token;
- `iss` — foi o nosso realm que emitiu;
- `exp` — não expirou;
- `aud` contém **`bff-api`** — o token foi emitido *para esta API*. A audience vem de um client
  scope com mapper atribuído só ao `app-cliente`; o token do M2M não a carrega;
- `realm_access.roles` → authorities do Spring: `cliente`, `analista`, `operador`.

Sem token ou com token inválido → **401** (não sei quem é você). Token válido sem a role exigida →
**403** (sei quem é, mas não pode). Ex.: `maria.cliente` em `GET /v1/analises`, que exige
`analista`.

**Na SPA.** Biblioteca `oidc-client-ts`, que gera `code_verifier` e `code_challenge` a cada login
e confere o `state` na volta. Token em `sessionStorage` (morre com a aba), tela montada com
`textContent` e nunca `innerHTML` (XSS é o que roubaria o token), e logout que encerra a sessão
no Keycloak, não só no navegador.

**Dados pessoais (LGPD, princípio da necessidade).** O access token, que viaja para a API a cada
requisição, leva só o que a API usa: `sub` (um UUID, não o CPF), roles, `aud` e o nome para
exibição. O **email ficou fora do access token** — continua apenas no ID token e no userinfo,
que são para a SPA. O CPF **nunca** vai em token nem em evento.

### Antipadrões evitados

| Antipadrão | Risco | Como evitamos |
|---|---|---|
| **Password grant** (ROPC) | a aplicação recebe a senha do usuário; removido no OAuth 2.1 | *Direct access grants* **desligado** no `app-cliente`. A senha só é digitada na tela do Keycloak |
| **Token sem expiração** | token roubado vale para sempre | access token de **5 minutos** (`accessTokenLifespan = 300`) |
| **Secret no client** | qualquer um extrai o secret do JavaScript | `app-cliente` é **público**; a prova de posse é o PKCE, um segredo descartável por login |
| **Implicit flow** | token volta na URL (histórico, logs) | só *Standard flow*; o que volta na URL é um código de uso único |
| **PKCE `plain`** | o challenge é o próprio verifier | só **S256**: o hash não tem volta |

## Alternativas consideradas

**Login próprio em cada serviço, ou só no BFF.** Tabela de usuários e sessão escritas por nós.
Descartada: duplica a lógica em Java e Python (ou concentra no BFF e deixa o M2M sem solução),
nos obriga a guardar e proteger senhas e não oferece login único entre aplicações. É reinventar,
com mais risco, o que o IdP já entrega.

**IdP gerenciado (Auth0, Amazon Cognito, Microsoft Entra ID).** Mesmos protocolos, sem servidor
para operar. Descartada **neste trabalho** porque a demo precisa rodar inteira no
`docker compose` e no `kind`, sem depender de conta externa nem de internet. Em produção, sem time
para manter o Keycloak, seria a escolha.

**BFF como client confidencial guardando o token (padrão *token handler*).** O navegador só
recebe um cookie `HttpOnly`; o token nunca chega ao JavaScript, o que elimina o roubo por XSS.
Descartada pelo custo: sessão no servidor, proteção contra CSRF e um BFF com estado — e o
enunciado cobra explicitamente PKCE em client público. É a evolução natural se o sistema for
para produção (ver *Quando não vale a pena*).

**Introspection em vez de validação local.** O BFF perguntaria ao Keycloak, a cada requisição, se
o token é válido. Detecta revogação na hora, mas coloca o Keycloak no caminho de toda chamada.
Descartada: a validação por JWKS não gera tráfego por requisição, e a janela de revogação fica
limitada pelos 5 minutos de expiração.

## Consequências e custos

**Melhorou**

- Senha em um lugar só. Nenhum serviço nosso vê, guarda ou valida senha.
- Java e Python usam bibliotecas padrão de mercado em vez de código de segurança próprio.
- Pessoa e máquina se autenticam pelo mesmo mecanismo (token assinado), cada uma com o fluxo certo.
- Roles definidas uma vez e lidas do token: o BFF não consulta banco para autorizar.

**Piorou**

- **Um componente crítico a mais.** Com o Keycloak fora do ar ninguém faz login novo. Tokens já
  emitidos seguem válidos até expirar, porque o JWKS fica em cache no BFF.
- **Logout não revoga o access token.** Quem capturou um token antes do logout consegue usá-lo
  por até 5 minutos. É o preço de validar localmente.
- **O token vive no navegador.** Exposto a XSS. Mitigado com `sessionStorage`, `textContent` e
  expiração curta — mitigado, não eliminado.
- **Configuração versionada à mão.** O realm é um JSON exportado do painel; mudar algo no painel
  e esquecer de exportar faz os ambientes divergirem. Em `start-dev` o export ainda exige parar o
  container, porque o banco H2 em arquivo fica travado.
- **Hostname do emissor.** O navegador vê o Keycloak em `localhost:8180` e os containers em
  `keycloak:8080`; sem fixar o hostname público (`KC_HOSTNAME`), o `iss` não bate e o BFF devolve
  401 dentro do Docker.
- **Secrets de desenvolvimento no repositório.** Os secrets dos clients confidenciais estão no
  JSON do realm. Aceitável só em ambiente local; em produção viriam de um cofre ou `Secret` do
  Kubernetes.
- **Os serviços atrás do BFF confiam na rede interna.** A validação completa acontece na borda;
  não é *zero trust*.

## Quando não vale a pena

- **Uma aplicação, um runtime, sem login único e sem chamadas M2M.** O login do próprio framework
  (sessão do Spring Security, por exemplo) resolve com um servidor a menos para operar.
- **A organização já tem IdP corporativo** (Entra ID, Okta). Federar com ele é melhor do que subir
  um Keycloak paralelo, com um segundo cadastro de usuários.
- **Ninguém para operar o Keycloak em produção** — atualizar, aplicar correções de segurança,
  cuidar do banco. Um IdP desatualizado é pior que um gerenciado.
- **Aplicação com risco alto de roubo de token** — um internet banking em produção, por exemplo.
  Aí manter o token fora do navegador (*token handler* no BFF) passa a valer o custo extra.

## Condição de falha

Reabrimos esta decisão se:

- algum requisito exigir **bloqueio imediato** de acesso (ex.: conta suspensa por fraude não pode
  usar o sistema nem por 5 minutos) → introspection, ou token bem mais curto com refresh;
- o realm exportado divergir do painel e quebrar o ambiente de um colega **mais de uma vez por
  sprint** → configuração declarativa (`keycloak-config-cli` ou Terraform) em vez de export manual;
- for encontrada qualquer falha de **XSS** na SPA → token handler no BFF, sem token no navegador.
