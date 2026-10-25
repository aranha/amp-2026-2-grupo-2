# C4 — Nível 3: Componentes do Serviço de Crédito

**Responsável:** Vinicius Eduardo · **Trilha:** `trilha/credito`

## Escopo

O diagrama apresenta o interior do `servico-credito` (Java/Spring Boot) e suas integrações externas com Kafka, PostgreSQL e cliente REST/BFF. Representa a arquitetura implementada nos pacotes DDD, HTTP/Persistência, CQRS e Domain Service.

### Componentes principais

- **Entrada REST:** `PropostaController` cria e aceita propostas; `ConsultaPropostaController` consulta `GET /v1/propostas/{id}`.
- **Aplicação:** `AceitarPropostaUseCase` coordena a aceitação, usa a porta `PersistenciaProposta` e a entidade `SolicitacaoDeCredito`.
- **Domínio:** `SolicitacaoDeCredito` protege a transição `CRIADA → ACEITA`; `Dinheiro` e `CPF` são Value Objects; `PropostaAceita` é um Domain Event.
- **Domain Service:** `CalculadoraCondicoesCredito` calcula condições (Price); **ainda não está conectado ao caso de uso/API**.
- **Persistência de escrita:** `JdbcPropostaRepository` implementa `PersistenciaProposta`, usando `credito.solicitacao`. A atualização condicional protege a invariante mesmo sob concorrência.
- **Entrada por evento:** `PropostaProjectionListener` consome `risco.elegibilidade.v1`, `antifraude.analise.v1`, `contratacao.contrato.v1`.
- **Projeção CQRS:** `PropostaProjectionService` atualiza `credito.proposta_view` e registra identificadores em `credito.proposta_eventos_processados` para idempotência.
- **Persistência de leitura:** `JdbcPropostaViewRepository` acessa apenas a projeção; o endpoint GET não usa a tabela de escrita.
- **Legado:** o serviço mantém outros listeners e serviços para reserva/compensação; foram agrupados como *listeners legados*, não detalhados para evitar poluir o nível 3.

## Limites e precisão

O uso de PostgreSQL no desenho representa **uma instância com isolamento lógico por schema**, não uma instância física exclusiva. O desenho do Domain Service representa implementação isolada, não integração ao fluxo. O Domain Event `PropostaAceita` não implica publicação Kafka automática. Os tópicos de entrada do CQRS não comprovam, por si só, alinhamento final dos schemas CloudEvents.

## Arquivos

- `n3-componente-credito.drawio`: fonte editável no diagrams.net.
- `n3-componente-credito.png`: exportação visual para apresentação.

## Como incorporar à branch

Copiar os dois arquivos para `docs/c4/`, depois executar `git add docs/c4/n3-componente-credito.drawio docs/c4/n3-componente-credito.png` e revisar `git diff --cached --stat` antes do commit.
