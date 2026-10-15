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
