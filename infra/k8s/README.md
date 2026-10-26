# Manifests Kubernetes

Dono: **Sesaque** · alvo: `kind` local e o job `deploy` do CI · decisão na
[ADR-011](../../docs/adr/ADR-011-docker-kubernetes-ci-cd.md#kubernetes)

| Arquivo | Recursos |
|---|---|
| `00-namespace.yaml` | Namespace `amp`, com Pod Security `baseline` (bloqueia) e `restricted` (avisa) |
| `10-configmap.yaml` | `amp-config`: endereços de Kafka, Postgres, Keycloak e OTLP; opções da JVM |
| `11-secret.yaml` | `amp-banco`: senha do superusuário e de cada usuário de serviço |
| `20-postgres.yaml` | ConfigMap com o `init.sql` + Service headless + StatefulSet com **PVC** (1 Gi) |
| `21-kafka.yaml` | Service headless + StatefulSet KRaft de um nó com **PVC** (2 Gi) |
| `30-bff.yaml` | Deployment (**2 réplicas**) + Service |
| `31-servico-credito.yaml` · `32-servico-risco.yaml` | Deployment + Service, Java |
| `33-servico-antifraude.yaml` · `34-servico-contratacao.yaml` | Deployment + Service, Python |
| `35-app-cliente.yaml` | Deployment + Service, nginx com a SPA |

**O que fica fora do cluster:** Keycloak e a stack de observabilidade continuam no compose. No
cluster, `OTEL_SDK_DISABLED=true` e o BFF responde 401 sem token, mas validar um token exige o
JWKS do Keycloak. O fluxo por evento (crédito → risco → antifraude → read model) roda inteiro
dentro do cluster.

## Subir local

```bash
scripts/k8s-local.sh              # cria o kind, builda as 6 imagens, carrega e aplica
scripts/k8s-local.sh --sem-build  # reaproveita imagens já construídas
scripts/k8s-local.sh --apagar     # remove o cluster
```

> Todo `kubectl` do script e da demo leva `--context kind-amp`. Rodando à mão, faça o mesmo: o
> contexto atual da sua máquina pode ser um cluster de verdade.

Acesso de fora do cluster:

```bash
kubectl --context kind-amp port-forward -n amp svc/bff 8080:8080
kubectl --context kind-amp port-forward -n amp svc/app-cliente 5173:80
```

## Padrões aplicados em todos os serviços

- **Probes:** `startupProbe` segura a liveness até a JVM subir; liveness e readiness em
  `/actuator/health/liveness` e `/actuator/health/readiness` nos Java, `/health` nos Python
- **Recursos:** `requests` de CPU e memória para o scheduler, `limits` só de memória — limite de
  CPU estrangula o boot da JVM sem proteger nada num cluster de laboratório
- **Segurança:** usuário sem root, `readOnlyRootFilesystem` com `/tmp` em `emptyDir`,
  `capabilities: drop ALL`, `seccompProfile: RuntimeDefault`. A exceção é o nginx da SPA
- **`enableServiceLinks: false`:** sem isso o Kubernetes injeta `KAFKA_PORT=tcp://...` pelo
  Service `kafka`, e a imagem do Kafka trata toda variável `KAFKA_*` como configuração do broker
- **Init container** `aguarda-postgres` nos serviços com banco: sem ele, o primeiro boot vira uma
  sequência de `CrashLoopBackOff` enquanto o Postgres inicializa
- **Rolling update** com `maxUnavailable: 0`: o pod novo fica pronto antes de o antigo sair

## Contrato com o CI

O job `deploy` do [`ci.yml`](../../.github/workflows/ci.yml) sobe um kind dentro do runner, carrega
nele as imagens que o próprio pipeline acabou de construir e aplica esta pasta. Por isso:

- a imagem é `ghcr.io/aranha/amp-2026-2-grupo-2/<servico>:latest` com
  `imagePullPolicy: IfNotPresent` — senão o nó tenta baixar do GHCR e o pod fica em `ErrImagePull`;
- todos os arquivos terminam em `.yaml`, sem `kustomization.yaml` (o `kubectl apply -f` tentaria
  aplicá-lo como recurso), e a numeração garante a ordem: namespace, configuração, dados, serviços;
- o job espera **todos** os Deployments do namespace `amp` ficarem `Available`.

## Demo de self-healing (passo 9)

```bash
scripts/demo.sh   # o passo 9 apaga o pod do crédito e mostra o ReplicaSet recriando
```

À mão:

```bash
kubectl --context kind-amp delete pod -n amp -l app=servico-credito
kubectl --context kind-amp get pods -n amp -w
```

O pod apagado aparece como `Error`: é a JVM saindo com código 143 ao receber o SIGTERM. O novo
fica `1/1 Running` em poucos segundos, e o PVC do Postgres e do Kafka garante que nenhum dado se
perde quando é o pod deles que cai.
