#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${CONTRACTING_URL:-http://localhost:8085}"

curl --fail --silent --show-error "${BASE_URL}/health"
printf '\nContratação disponível em %s\n' "${BASE_URL}"
printf 'O fluxo de negócio é acionado publicando credito.proposta.v1 no Kafka.\n'
