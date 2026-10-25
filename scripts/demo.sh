#!/usr/bin/env bash
# Roteiro da demo — 8 min, nove passos, na ordem da apresentação.
# Dono: Marcus Santos. Nada é digitado ao vivo: a gravação de 03/11 roda este script.
#
# Detalhe de cada passo:
#   docs/divisao-de-trabalho.md#roteiro-da-demo-8-min
set -euo pipefail

BFF=${BFF:-http://localhost:8080}
KC=${KC:-http://localhost:8180}

passo() { printf '\n\033[1;36m▸ %s\033[0m\n' "$*"; }

passo "1/9 · Containers de pé"
docker compose ps

passo "2/9 · Login PKCE → 200, 401 sem token, 403 com role errada"
# Dono: Rafael. Login de pessoa é no navegador, de propósito: o password grant está desligado,
# então nenhum curl consegue entrar como a Ana ou a Maria. O curl mostra o que não depende de pessoa.
SPA=${SPA:-http://localhost:5173}
REALM_JSON=${REALM_JSON:-$(dirname "$0")/../infra/keycloak/realm-credito.json}

analises() {  # $1 = access token (vazio = sem token) → status HTTP de GET /v1/analises
  local args=(-s -o /dev/null -w '%{http_code}')
  [ -n "${1:-}" ] && args+=(-H "Authorization: Bearer $1")
  curl "${args[@]}" "$BFF/v1/analises"
}

payload() {  # o meio do JWT é só base64url: decodifica sem chave nenhuma
  local p
  p=$(printf '%s' "$1" | cut -d. -f2 | tr '_-' '/+')
  while [ $(( ${#p} % 4 )) -ne 0 ]; do p="$p="; done
  printf '%s' "$p" | base64 -d 2>/dev/null || true
}

echo "  No navegador — $SPA"
echo "    1. Entrar como ana.analista → aba Análises → Carregar análises      → 200"
echo "    2. Chamar sem token                                                → 401"
echo "    3. Sair → entrar como maria.cliente → Análises → Carregar análises → 403"
if [ -t 0 ]; then read -r -p "  ↵ Enter quando terminar no navegador " _ || true; fi

echo
echo "  Pelo curl, sem navegador:"
echo "    GET /v1/analises sem token              → $(analises "")"

M2M_SECRET=${M2M_SECRET:-$(awk '/"clientId" : "servico-contratacao-m2m"/{f=1} f && /"secret" :/{gsub(/[",]/, "", $3); print $3; exit}' "$REALM_JSON")}
TOKEN_M2M=$(curl -s -X POST "$KC/realms/credito/protocol/openid-connect/token" \
  -d grant_type=client_credentials -d client_id=servico-contratacao-m2m -d "client_secret=$M2M_SECRET" \
  | sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p' || true)
echo "    token M2M (client credentials): $(payload "$TOKEN_M2M" | grep -o '"preferred_username":"[^"]*"' || true)"
echo "                                    $(payload "$TOKEN_M2M" | grep -o '"aud":[^,]*' || true)"
echo "    GET /v1/analises com o token M2M        → $(analises "$TOKEN_M2M")  token válido, mas não emitido para o BFF"
curl -s -D - -o /dev/null -H "Authorization: Bearer $TOKEN_M2M" "$BFF/v1/analises" \
  | grep -io 'error_description="[^"]*"' | sed 's/^/      /' || true

# Plano B, se o navegador falhar na gravação: na SPA, aba "Meu token" → "Copiar access token",
# depois `export TOKEN_ANA=...` e `export TOKEN_MARIA=...` antes de rodar. O token vale 5 minutos.
if [ -n "${TOKEN_ANA:-}" ]; then
  echo "    GET /v1/analises como ana.analista      → $(analises "$TOKEN_ANA")"
fi
if [ -n "${TOKEN_MARIA:-}" ]; then
  echo "    GET /v1/analises como maria.cliente     → $(analises "$TOKEN_MARIA")"
fi

passo "3/9 · Fluxo por evento: crédito → risco → antifraude → contratação"
# TODO Hugo

passo "4/9 · Invariante do agregado: 409 ao aceitar a proposta duas vezes"
# TODO Vinicius

passo "5/9 · CQRS: leitura do read model populado por evento"
# TODO Vinicius

passo "6/9 · Circuit breaker abrindo e fechando"
# TODO Paulo

passo "7/9 · /v1 e /v2 convivendo"
# TODO Paulo

passo "8/9 · Grafana: trace atravessando BFF → serviço → fila"
# TODO Sesaque

passo "9/9 · Kubernetes: self-healing"
# TODO Sesaque
