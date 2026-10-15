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

**Demo de self-healing** (passo 9) — gravar na Sprint 2, enquanto dá tempo:

```bash
kubectl delete pod -n amp -l app=servico-credito
kubectl get pods -n amp -w
```
