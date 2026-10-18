# Concessão de Crédito — Arquitetura Multiplataforma

**PUC Minas · Arquitetura Multiplataforma · 2026/2 · Grupo 2**

Arquitetura multiplataforma para o processo de concessão de crédito: quatro serviços de domínio
em duas linguagens, um BFF, mensageria por eventos, identidade federada e os três sinais de
observabilidade.

Comece por **[docs/plano-de-trabalho.md](docs/plano-de-trabalho.md)** — a arquitetura e o porquê
de cada escolha.

**Acompanhar o progresso:** [issue #1 — Checklist de entrega](../../issues/1), com os 113 itens do
enunciado em task lists. O GitHub conta o progresso sozinho; marcar exige ser colaborador do
repositório.

| Documento | Para quê |
|---|---|
| [Plano de trabalho](docs/plano-de-trabalho.md) | Arquitetura alvo, trade-offs e cronograma |
| [Divisão de trabalho](docs/divisao-de-trabalho.md) | Quem faz o quê, prazos e dependências |
| [Checklist de entrega](docs/checklist-entrega.md) | Auditoria item a item do enunciado |
| [ADRs](docs/adr/README.md) | As onze decisões, cada uma com o "quando não vale" |
| [Herança da disciplina anterior](docs/heranca-aed/README.md) | O que veio do EventStorming de AED |

---

## Integrantes

| Nome | Matrícula | Trilha |
|---|---|---|
| Paulo Euclydes Aranha Junior **(líder)** | 255171 | Arquitetura e BFF |
| Vinícius Eduardo Silva Oliveira | 1310290 | Crédito e CQRS |
| Guilherme *(nome completo a confirmar)* | *a confirmar* | Risco e CI/CD |
| Hugo Fontolan Piani | 258724 | Antifraude (Python) |
| Marcus Vinicius da Cruz Santos | 255495 | Contratação (Python) |
| Rafael Oliveira de Lima | 258889 | Segurança e identidade |
| Sesaque de Oliveira da Cruz | 254176 | Observabilidade e Kubernetes |

---

## Os componentes

| Componente | Runtime | Contexto | Schema | Porta |
|---|---|---|---|---|
| `app-cliente` | SPA (Vite) | — | — | 5173 |
| `bff` | Java 21 / Spring Boot | borda | — | 8080 |
| `servico-credito` | Java 21 / Spring Boot | Crédito | `credito` | 8082 |
| `servico-risco` | Java 21 / Spring Boot | Risco | `risco` | 8083 |
| `servico-antifraude` | Python 3.12 / FastAPI | Antifraude | `antifraude` | 8084 |
| `servico-contratacao` | Python 3.12 / FastAPI | Contratação + Liberação | `contratacao` | 8085 |

Nenhum serviço de domínio chama outro de forma síncrona — só eventos. Nenhum JOIN cruza schema.

---

## Pré-requisitos

- Java 21 · Maven
- Python 3.12
- Docker e Docker Compose
- `kubectl` e `kind` *(só para a parte de Kubernetes)*

---

## Como rodar

### 1. Infraestrutura

```bash
docker compose up -d
```

Sobe broker, banco, IdP e a stack de observabilidade:

| Serviço | URL | Credenciais |
|---|---|---|
| Kafka | `localhost:19092` | — |
| Kafka UI | http://localhost:8081 | — |
| PostgreSQL | `localhost:15432` | `amp` / `amp` |
| Keycloak | http://localhost:8180 | `admin` / `admin` |
| Grafana | http://localhost:3000 | `admin` / `admin` |
| Prometheus | http://localhost:9090 | — |
| Tempo | http://localhost:3200 | — |
| Loki | http://localhost:3100 | — |
| OTLP | `localhost:4317` (gRPC) · `4318` (HTTP) | — |

Conferir:

```bash
docker compose ps
```

### 2. Os serviços

```bash
docker compose --profile apps up -d
```

*Cada dono habilita o seu serviço no compose na Sprint 0, junto com o Dockerfile multi-stage.*

### 3. A demo

```bash
./scripts/demo.sh
```

Os nove passos do roteiro, na ordem da apresentação. Detalhe em
[docs/divisao-de-trabalho.md](docs/divisao-de-trabalho.md#roteiro-da-demo-8-min).

### Derrubar tudo

```bash
docker compose down -v
```

---

## Como contribuir

- Branch por trilha: `trilha/<nome>` → PR para `main` → **1 aprovação**
- `main` sempre verde: o pipeline roda em todo PR
- **Padrão novo exige ADR.** PR sem ela é devolvido
- Teste de domínio sem framework e sem banco — é a prova da regra de dependência

---

## Entrega

| Data | O quê |
|---|---|
| dom 18/10 | Portão da Sprint 0 — infraestrutura e contratos |
| dom 25/10 | Portão da Sprint 1 — domínio e segurança |
| dom 01/11 | Portão da Sprint 2 — resiliência e observabilidade |
| seg 02/11 | Code freeze 12h · auditoria do checklist · ensaio |
| ter 03/11 | Gravação do vídeo |
| **qua 04/11** | **Apresentação** |
