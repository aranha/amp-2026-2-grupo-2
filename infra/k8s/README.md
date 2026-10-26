# Manifests Kubernetes

Dono: **Sesaque** · alvo: `kind` local

| Arquivo | Recursos |
|---|---|
| `00-namespace.yaml` | Namespace `amp` |
| `10-configmap.yaml` | Endpoints de Kafka, Postgres, Keycloak e OTLP |
| `11-secret.yaml` | Senhas de banco e client secrets |
| `20-postgres.yaml` | StatefulSet + **PVC** + Service |
| `30-<servico>.yaml` | Deployment + Service + liveness/readiness, um por serviço |

```bash
kind create cluster --name amp
kubectl apply -f infra/k8s/
kubectl rollout status -n amp deployment --timeout=180s
```

## Contrato com o CI

O job `deploy` do [`ci.yml`](../../.github/workflows/ci.yml) sobe um kind dentro do runner, carrega
nele as imagens que o próprio pipeline acabou de construir e aplica esta pasta
([ADR-011](../../docs/adr/ADR-011-docker-kubernetes-ci-cd.md)). Para isso os manifests precisam:

- usar a imagem `ghcr.io/aranha/amp-2026-2-grupo-2/<servico>:latest` com
  `imagePullPolicy: IfNotPresent` — senão o nó tenta baixar do GHCR e o pod fica em `ErrImagePull`;
- terminar em `.yaml` e criar os Deployments no namespace `amp`: o job espera **todos** eles
  ficarem `Available`.

Enquanto não houver `.yaml` aqui, o job pula o deploy com um aviso. Nos serviços Java, as probes
são `/actuator/health/liveness` e `/actuator/health/readiness`, na porta 8080.

**Demo de self-healing** (passo 9) — gravar na Sprint 2, enquanto dá tempo:

```bash
kubectl delete pod -n amp -l app=servico-credito
kubectl get pods -n amp -w
```
