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
# Dono: Hugo.
eventos() {  # $1 = solicitacaoId → tópico, produtor e partição de cada evento dessa solicitação
  MSYS_NO_PATHCONV=1 docker exec amp-kafka /opt/kafka/bin/kafka-console-consumer.sh \
    --bootstrap-server kafka:9094 --include '(credito|risco|antifraude|contratacao)\.[a-z.-]+\.v1' \
    --from-beginning --timeout-ms 5000 --property print.timestamp=true --property print.partition=true \
    --property print.headers=true --property print.key=true --property print.value=false 2>/dev/null \
    | grep -a "$1" | sort -t: -k2 -n \
    | awk -F'\t' '{ match($3, /ce_type:[^,]*/); t = substr($3, RSTART + 8, RLENGTH - 8)
                    match($3, /ce_source:[^,]*/); s = substr($3, RSTART + 10, RLENGTH - 10)
                    split($2, p, ":"); printf "    %-34s %-24s partição %s\n", t, s, p[2] }' || true
}

if [ -n "${TOKEN_MARIA:-}" ]; then
  SOLICITACAO=$(curl -s -X POST "$BFF/v1/solicitacoes" -H "Authorization: Bearer $TOKEN_MARIA" \
    -H 'Content-Type: application/json' -d '{"valorSolicitado": 18500.00}' \
    | sed -n 's/.*"solicitacaoId":"\([^"]*\)".*/\1/p')
elif [ -t 0 ]; then
  echo "  No navegador — ${SPA:-http://localhost:5173}: maria.cliente → aba Solicitar → R\$ 18.500,00"
  read -r -p "  solicitacaoId devolvido: " SOLICITACAO || true
fi

if [ -z "${SOLICITACAO:-}" ]; then
  echo "  Sem solicitação: exporte TOKEN_MARIA (plano B do passo 2) ou rode num terminal interativo"
else
  echo "  Solicitação $SOLICITACAO — eventos com essa chave, em ordem:"
  for _ in 1 2 3 4 5 6; do
    lista=$(eventos "$SOLICITACAO")
    grep -q antifraude.analise.v1 <<<"$lista" && break
  done
  echo "$lista"
  echo "  No Kafka UI: ${KAFKA_UI:-http://localhost:8081} → Topics → mensagens com a chave acima"
  if [ -n "${TOKEN_MARIA:-}" ]; then
    echo "  Bloco antifraude do GET /v2/propostas/$SOLICITACAO:"
    curl -s -H "Authorization: Bearer $TOKEN_MARIA" "$BFF/v2/propostas/$SOLICITACAO" \
      | grep -o '"antifraude":{[^}]*}' | sed 's/^/    /' || true
  fi
fi

passo "4/9 · Invariante do agregado: 409 ao aceitar a proposta duas vezes"
# TODO Vinicius

passo "5/9 · CQRS: leitura do read model populado por evento"
# TODO Vinicius

passo "6/9 · Circuit breaker abrindo e fechando"
# Dono: Paulo. O alvo é o antifraude porque é o único downstream com fallback: com ele fora, o
# BFF degrada em vez de devolver 500 — a proposta continua respondendo, sem o bloco de análise.
# Config em bff/src/main/resources/application.yml: janela de 10 chamadas, 50% de falha, 10s
# aberto, 3 chamadas em half-open. O /actuator/health não pede token e traz os três breakers,
# então a plateia vê o estado virar sem nada escondido atrás de um Bearer.
ANTIFRAUDE=${ANTIFRAUDE:-http://localhost:8084}

campo() {  # $1 = bloco do breaker, $2 = campo. O ":" no grep evita casar failureRateThreshold.
  printf '%s' "$1" | tr ',' '\n' | grep "\"$2\":" | cut -d: -f2 | tr -d '"' | head -1 || true
}

breaker() {  # $1 = nome → só os detalhes desse breaker dentro do /actuator/health
  curl -s "$BFF/actuator/health" \
    | sed "s/.*\"$1\":{//" | sed 's/}}.*//' | sed 's/.*"details":{//' || true
}

estado() {  # uma linha por breaker: estado, taxa de falha e quanto da janela já encheu
  local bloco taxa
  bloco=$(breaker "$1")
  taxa=$(campo "$bloco" failureRate)
  if [ "$taxa" = "-1.0%" ]; then taxa="janela vazia"; fi
  printf '    %-11s %-10s %-13s %s de %s chamadas\n' \
    "$1" "$(campo "$bloco" state)" "$taxa" \
    "$(campo "$bloco" failedCalls)" "$(campo "$bloco" bufferedCalls)"
}

breakers() { for b in credito risco antifraude; do estado "$b"; done; }

chamar() {  # uma consulta à proposta → só o status HTTP
  curl -s -o /dev/null -w '%{http_code}' -H "Authorization: Bearer ${TOKEN_MARIA:-}" \
    "$BFF/v2/propostas/${SOLICITACAO:-}" || true
}

if [ -z "${TOKEN_MARIA:-}" ] || [ -z "${SOLICITACAO:-}" ]; then
  echo "  Precisa do TOKEN_MARIA e da solicitação dos passos 2 e 3."
  echo "  O estado dos breakers não depende de token, então ainda dá para mostrar:"
  breakers
else
  echo "  Fechados, antes de derrubar nada:"
  breakers

  echo
  echo "  docker stop amp-servico-antifraude"
  docker stop amp-servico-antifraude >/dev/null 2>&1 || true

  echo "  Dez chamadas a /v2/propostas — é exatamente o tamanho da janela:"
  linha="   "
  for _ in 1 2 3 4 5 6 7 8 9 10; do linha="$linha $(chamar)"; done
  echo "$linha"
  echo "    Nenhum 500: o fallback do AntifraudeClient devolve a proposta sem a análise."

  echo
  echo "  Aberto — o BFF parou de bater no serviço caído:"
  breakers
  echo "    E avisa o cliente de que a degradação é parcial:"
  curl -s -H "Authorization: Bearer $TOKEN_MARIA" "$BFF/v2/propostas/$SOLICITACAO" \
    | grep -o '"antifraudeIndisponivel":[a-z]*' | sed 's/^/      /' || true

  echo
  echo "  docker start amp-servico-antifraude"
  docker start amp-servico-antifraude >/dev/null 2>&1 || true
  for _ in $(seq 1 40); do curl -fsS -o /dev/null "$ANTIFRAUDE/health" 2>/dev/null && break; sleep 1; done

  echo "  Esperando a janela de 10s — o Resilience4j vai sozinho para half-open:"
  for _ in $(seq 1 25); do
    [ "$(campo "$(breaker antifraude)" state)" = "OPEN" ] || break
    sleep 1
  done
  breakers

  echo "    Três chamadas, que é o que half-open deixa passar:"
  linha="     "
  for _ in 1 2 3; do linha="$linha $(chamar)"; done
  echo "$linha"

  echo
  echo "  Fechado de novo, sem ninguém mexer em nada:"
  breakers
fi

passo "7/9 · /v1 e /v2 convivendo"
# Dono: Paulo. A mesma proposta nas duas versões, no mesmo BFF. A v1 devolve os campos planos
# que o cliente antigo espera; a v2 põe as condições num objeto e acrescenta o cet. Tirar
# "taxa" do topo quebra quem já lê de lá — é breaking change, e é por isso que existe uma v2
# inteira em vez de um campo novo na v1. A v1 não morre: avisa, com Deprecation e Sunset.

if [ -z "${TOKEN_MARIA:-}" ] || [ -z "${SOLICITACAO:-}" ]; then
  echo "  Precisa do TOKEN_MARIA e da solicitação dos passos 2 e 3."
else
  CABECALHOS=$(mktemp)

  echo "  GET /v1/propostas/$SOLICITACAO"
  curl -s -D "$CABECALHOS" -H "Authorization: Bearer $TOKEN_MARIA" \
    "$BFF/v1/propostas/$SOLICITACAO" | sed 's/^/    /' || true
  echo

  echo "  O que a v1 responde a quem ainda está nela:"
  grep -iE '^(deprecation|sunset|link):' "$CABECALHOS" | sed 's/^/    /' || true
  rm -f "$CABECALHOS"
  echo

  echo "  GET /v2/propostas/$SOLICITACAO"
  curl -s -H "Authorization: Bearer $TOKEN_MARIA" \
    "$BFF/v2/propostas/$SOLICITACAO" | sed 's/^/    /' || true
  echo
  echo "    taxa e parcelas saem do topo e viram condicoes, com o cet junto."
  echo "    Mesma rota, mesmo token, duas versões servidas ao mesmo tempo."
fi

passo "8/9 · Grafana: trace atravessando BFF → serviço → fila"
# TODO Sesaque

passo "9/9 · Kubernetes: self-healing"
# TODO Sesaque
