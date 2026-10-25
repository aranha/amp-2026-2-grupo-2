# ADR-011 — Docker multi-stage, Kubernetes e CI/CD

## Status

Proposto. Docker e CI/CD pela `trilha/risco` (Guilherme); a seção de Kubernetes registra o que o
plano já decidiu e fecha com os manifests da `trilha/plataforma` (Sesaque).

## Contexto

- **Seis componentes, três toolchains.** Maven no `bff`, no `servico-credito` e no
  `servico-risco`; pip no `servico-antifraude` e no `servico-contratacao`; npm no `app-cliente`.
- **O enunciado cobra o caminho inteiro:** imagem multi-stage por serviço (B57), orquestração com
  Deployment, Service, ConfigMap, Secret e self-healing (B58–B61), e CI/CD com teste, build, push
  e deploy (B62) — com teste automatizado em mais de uma linguagem no pipeline (B67) e dependências
  geridas por plataforma (B64).
- **`main` sempre verde**, sete pessoas fazendo merge na mesma semana, PR com uma aprovação.
- **Sem orçamento de nuvem.** Tudo precisa rodar num runner gratuito do GitHub e no notebook de
  cada um.
- **O pipeline em 25/10:** testes em todo PR; build e push num job só e só na `main`, com o cache
  do Buildx compartilhado entre os serviços; dois serviços sem Dockerfile faziam o build falhar e,
  com `fail-fast`, cancelavam os outros quatro. O deploy nunca tinha rodado — a `main` estava
  vermelha desde o primeiro merge.

## Decisão

### Imagens

**Multi-stage em todos os serviços:** o estágio de build tem o toolchain completo; o de runtime,
só o necessário para rodar.

| Serviço | Build | Runtime |
|---|---|---|
| Java (`bff`, `servico-credito`, `servico-risco`) | `maven:3.9-eclipse-temurin-21` | `eclipse-temurin:21-jre-alpine` |
| Python (`servico-antifraude`, `servico-contratacao`) | `python:3.12-slim`, gera as wheels | `python:3.12-slim`, instala as wheels |
| SPA (`app-cliente`) | `node:22-alpine`, `npm run build` | `nginx:1.27-alpine` com o `dist/` |

Regras comuns: dependências numa camada própria, antes do código (`COPY pom.xml` +
`dependency:go-offline`), para que mudar só o código não baixe tudo de novo; processo sem root no
runtime; `HEALTHCHECK` na própria imagem — nos Java, `/actuator/health/readiness`. Os testes **não**
rodam no `docker build`: rodam uma vez, no job de teste. Dentro da rede, os serviços Java escutam
em 8080; no host, cada um tem sua porta.

### Pipeline — `.github/workflows/ci.yml`

```
teste-java   (bff · crédito · risco)        ─┐
                                             ├─► build (6 imagens) ─► push (GHCR) ─► deploy (kind)
teste-python (antifraude · contratação)     ─┘
```

| Job | Quando | O que faz | Por quê |
|---|---|---|---|
| `teste-java` · `teste-python` | PR e `main` | Maven e pytest **em paralelo**, uma perna por serviço, sem `fail-fast` | é a prova do B67; um serviço quebrado não esconde o estado dos outros |
| `build` | PR e `main` | Buildx de cada Dockerfile, cache do GitHub Actions **por serviço**; na `main`, exporta a imagem como artefato | Dockerfile quebrado aparece no PR, antes do merge |
| `push` | só `main` | publica no GHCR **o artefato do build**, com a tag do commit e `latest` | a imagem publicada é bit a bit a que foi construída — não um segundo build |
| `deploy` | só `main` | cluster `kind` dentro do runner, `kind load` das mesmas imagens, `kubectl apply -f infra/k8s/`, espera os Deployments ficarem `Available` | prova a orquestração sem cluster pago e sem credencial do GHCR dentro do cluster |

Detalhes que valem como decisão:

- **Permissões mínimas.** O workflow é `contents: read`; só o `push` recebe `packages: write`.
- **PR com push novo cancela a execução anterior**; na `main`, nada é cancelado.
- **Serviço sem Dockerfile falha sozinho**, com a mensagem `ainda não tem Dockerfile multi-stage
  (checklist B57)`, sem cancelar os outros. O `push` e o `deploy` só rodam com as seis imagens
  prontas.
- **Contrato com os manifests:** imagem `ghcr.io/aranha/amp-2026-2-grupo-2/<servico>:latest` com
  `imagePullPolicy: IfNotPresent` — o nó do kind usa a imagem carregada pelo job em vez de buscar
  no GHCR. Enquanto `infra/k8s/` não tiver `.yaml`, o deploy é pulado com aviso.
- **Plano B**, se o kind não fechar a tempo: `kubectl apply -f infra/k8s/ --dry-run=server`,
  já comentado no job.

### Kubernetes

Do plano de trabalho (§3.9), a confirmar pela `trilha/plataforma`: alvo `kind`; namespace `amp`;
Deployment e Service por serviço; ConfigMap com os endereços de Kafka, Postgres, Keycloak e OTLP;
Secret com as senhas; Postgres em StatefulSet com PVC; liveness e readiness em todos — nos Java,
`/actuator/health/liveness` e `/actuator/health/readiness` na 8080. Self-healing demonstrado
apagando um pod e vendo o ReplicaSet recriar.

## Alternativas consideradas

**Imagem de um estágio só, com JDK e Maven.** Mais simples de escrever. Descartada: o runtime
levaria compilador, Maven e o repositório local de dependências — mais superfície de ataque e mais
tamanho para mover a cada deploy —, e multi-stage é o item cobrado.

**Jib ou Buildpacks (`spring-boot:build-image`).** Sem Dockerfile, com camadas bem separadas.
Descartados porque só resolvem os três Java: Python e SPA precisariam de outro caminho, e teríamos
dois jeitos de construir imagem no mesmo repositório.

**Distroless no runtime.** Menor ainda e sem shell. Descartado por ora: sem shell não há `wget`
para o `HEALTHCHECK`, nem como entrar no container durante a demo.

**Build de novo no push, em vez de artefato.** Transfere menos dados entre jobs. Descartado porque
a imagem publicada poderia diferir da construída — as bases `-alpine` e `-slim` mudam entre uma
execução e outra.

**Cluster de verdade (EKS, GKE, AKS) ou o Kubernetes do Docker Desktop.** Mais próximo de
produção. Descartado: custo e credencial no repositório, ou dependência da máquina de alguém. O
kind no runner nasce e morre com o job.

**GitOps com Argo CD.** É o caminho de produção para o deploy. Fora do que as aulas cobriram
(plano §4) — entra no slide do que não fizemos.

## Consequências e custos

**Melhorou**

- Dockerfile quebrado aparece no PR, não depois do merge na `main`.
- Um serviço sem Dockerfile falha sozinho e diz por quê, em vez de cancelar os outros.
- O que vai para o GHCR e para o kind é exatamente o que o build produziu.
- Cache por serviço: sem mudança de dependência, o build reaproveita a camada do Maven ou do pip.

**Piorou**

- **Artefatos pesados.** Exportada, a imagem do risco tem 121 MB; as seis somam perto de meio
  gigabyte por execução na `main`, e upload e download custam minutos. A retenção é de um dia.
- **Três toolchains para manter** no pipeline, cada um com sua versão e seu cache.
- **O deploy prova que os manifests sobem, não que o sistema funciona.** Sem Kafka e Keycloak no
  cluster, os serviços sobem sem as dependências; o fluxo de negócio continua demonstrado no compose.
- **`main` vermelha por desenho** enquanto houver serviço sem Dockerfile: o `build` falha e o
  `push` e o `deploy` não rodam.
- **Actions de terceiros por tag de versão** (`@v7`), não por SHA: recebem correção sem mudar o
  arquivo, mas confiam no mantenedor.

## Quando não vale a pena

- **Um serviço, um toolchain.** Um job com `mvn verify` e `docker build` resolve; matriz,
  artefatos e deploy em kind seriam excesso.
- **Kubernetes para seis containers de demonstração.** O compose resolve. Usamos porque a
  disciplina cobra orquestração e self-healing; com ~1.000 solicitações por dia, uma VM com compose
  ou um PaaS custariam menos em produção.
- **Deploy em kind que ninguém olha.** Se o resultado não é conferido, o job é teatro.
- **Imagem grande que muda pouco.** Mover artefato entre jobs pode custar mais que reconstruir com
  cache.

## Condição de falha

Reabrimos esta decisão se:

- o pipeline de um PR passar de **10 minutos** — o mesmo gatilho da ADR-003 —, caso em que entram
  filtros de caminho por serviço;
- a `main` ficar vermelha por mais de **48 horas**, o mesmo prazo de redistribuição de trilha;
- o job de deploy passar **uma sprint inteira** pulado ou desligado: ou volta a rodar, ou sai do
  slide.
