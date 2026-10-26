#!/usr/bin/env bash
# Sobe o trabalho num cluster kind local, do mesmo jeito que o job deploy do CI (ADR-011):
# builda as imagens com a tag do GHCR, carrega no nó do kind e aplica infra/k8s/.
# Dono: Sesaque (trilha/plataforma).
#
#   scripts/k8s-local.sh              # cria o cluster se preciso, builda, carrega e aplica
#   scripts/k8s-local.sh --sem-build  # reaproveita as imagens que já estão no Docker local
#   scripts/k8s-local.sh --apagar     # remove o cluster
#
# Todo kubectl leva --context kind-amp: o contexto atual da sua máquina pode ser um cluster de
# verdade, e este script nunca deve aplicar nada fora do kind.
set -euo pipefail

CLUSTER=amp
CONTEXTO=kind-$CLUSTER
REGISTRY=ghcr.io/aranha/amp-2026-2-grupo-2
SERVICOS=(bff servico-credito servico-risco servico-antifraude servico-contratacao app-cliente)
INFRA=(postgres:16-alpine apache/kafka:3.9.1)
RAIZ=$(cd "$(dirname "$0")/.." && pwd)

k() { kubectl --context "$CONTEXTO" "$@"; }
etapa() { printf '\n\033[1;36m▸ %s\033[0m\n' "$*"; }

for ferramenta in docker kind kubectl; do
  command -v "$ferramenta" >/dev/null || { echo "Falta o $ferramenta no PATH"; exit 1; }
done

if [ "${1:-}" = "--apagar" ]; then
  kind delete cluster --name "$CLUSTER"
  exit 0
fi

etapa "Cluster kind '$CLUSTER'"
if kind get clusters 2>/dev/null | grep -qx "$CLUSTER"; then
  echo "  já existe"
else
  kind create cluster --name "$CLUSTER" --wait 120s
fi

if [ "${1:-}" != "--sem-build" ]; then
  etapa "Build das imagens (multi-stage, as mesmas do CI)"
  for servico in "${SERVICOS[@]}"; do
    [ -f "$RAIZ/$servico/Dockerfile" ] || { echo "  $servico ainda não tem Dockerfile"; exit 1; }
    echo "  $servico"
    docker build -q -t "$REGISTRY/$servico:latest" "$RAIZ/$servico" >/dev/null
  done
fi

etapa "Carregando as imagens no nó do kind"
for imagem in "${INFRA[@]}"; do
  docker image inspect "$imagem" >/dev/null 2>&1 || docker pull -q "$imagem" >/dev/null
done
imagens=("${INFRA[@]}")
for servico in "${SERVICOS[@]}"; do imagens+=("$REGISTRY/$servico:latest"); done
# Uma por vez: o kind exporta cada lote num .tar temporário, e o lote inteiro passa de 1,5 GB.
for imagem in "${imagens[@]}"; do
  kind load docker-image --name "$CLUSTER" "$imagem" | grep -v "already present" || true
done

etapa "kubectl apply -f infra/k8s/"
ja_aplicado=$(k get deployment -n amp -o name 2>/dev/null || true)
k apply -f "$RAIZ/infra/k8s/"
# Imagem nova com a mesma tag latest não muda o Deployment: o restart força os pods novos.
[ -n "$ja_aplicado" ] && k rollout restart deployment -n amp >/dev/null

etapa "Esperando o banco, o broker e os Deployments"
k rollout status statefulset/postgres -n amp --timeout=180s
k rollout status statefulset/kafka -n amp --timeout=180s
k wait --for=condition=Available deployment --all -n amp --timeout=300s
k get pods -n amp -o wide

cat <<EOF

Pronto. Para usar de fora do cluster:
  kubectl --context $CONTEXTO port-forward -n amp svc/bff 8080:8080
  kubectl --context $CONTEXTO port-forward -n amp svc/app-cliente 5173:80
Self-healing (passo 9 da demo):
  kubectl --context $CONTEXTO delete pod -n amp -l app=servico-credito
  kubectl --context $CONTEXTO get pods -n amp -w
EOF
