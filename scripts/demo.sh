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
# TODO Rafael

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
# TODO Paulo

passo "7/9 · /v1 e /v2 convivendo"
# TODO Paulo

passo "8/9 · Grafana: trace atravessando BFF → serviço → fila"
# TODO Sesaque

passo "9/9 · Kubernetes: self-healing"
# TODO Sesaque
