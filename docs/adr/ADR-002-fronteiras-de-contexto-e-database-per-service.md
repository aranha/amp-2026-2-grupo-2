# ADR-002 — Fronteiras de contexto e Database per Service

## Status

**Aceito** — sujeito à revisão e aceite do grupo no pull request.

## Contexto

O sistema de concessão de crédito distribui responsabilidades entre os contextos **Crédito**, **Risco**, **Antifraude** e **Contratação**. Crédito mantém solicitações, reservas de limite e uma projeção de consulta de propostas; os demais contextos produzem informações sobre elegibilidade, análise antifraude e contratos. A consulta consolidada no crédito recebe atualizações por eventos Kafka, incluindo `risco.elegibilidade.v1`, `antifraude.analise.v1` e `contratacao.contrato.v1`.

Sem limites explícitos de propriedade de dados, um serviço poderia ler ou modificar tabelas de outro contexto, criando acoplamento de esquema e tornando mudanças locais dependentes dos demais times.

No ambiente local inspecionado em **22/10/2026**, há **uma instância PostgreSQL compartilhada** com schemas separados (`credito`, `risco`, `antifraude`, `contratacao`) e usuários específicos. No schema `credito`, as seis tabelas `cancelamento_reserva`, `limite_credito`, `proposta_eventos_processados`, `proposta_view`, `reserva_limite` e `solicitacao` pertencem a `credito_user`.

Consultas com `has_schema_privilege` confirmaram para `credito_user` acesso (`USAGE`) e criação (`CREATE`) em `credito`, mas **sem** esses privilégios nos schemas `risco`, `antifraude` e `contratacao`. A role tem login e não possui atributos globais de superusuário, criação de bancos ou criação de roles. Essas verificações demonstram isolamento lógico de schema; **não** comprovam isolamento físico, nem auditoria exaustiva de toda permissão possível.

## Decisão

Adotar fronteiras de contexto por serviço: cada contexto é o único responsável pela escrita de seus dados e dispõe de **schema e usuário próprios** no PostgreSQL compartilhado. A troca de dados entre contextos ocorrerá por APIs e eventos publicados em contratos versionados, **sem consultas SQL entre schemas de negócio**.

Esta é uma implementação de **Database per Service com isolamento lógico** (schema por serviço), e não um banco ou servidor físico por serviço. O usuário `credito_user` pode acessar os objetos necessários de `credito` e não terá privilégios de `USAGE`/`CREATE` nos schemas dos outros contextos.

## Alternativas consideradas

1. **Schema único compartilhado com acesso direto entre serviços.** Rejeitado por permitir dependências SQL cruzadas, ampliar a superfície de acesso e acoplar implantações e mudanças de tabelas.
2. **Uma instância PostgreSQL separada para cada serviço.** Adiada por ampliar custo de operação, infraestrutura, monitoramento, backups e desenvolvimento local neste projeto acadêmico. Oferece isolamento físico mais forte e pode ser revisitada se surgirem necessidades operacionais ou regulatórias.
3. **Banco de documentos ou armazenamento orientado a eventos para todos os serviços.** Não adotado porque as operações transacionais atuais do crédito se adequam ao PostgreSQL e a migração não resolveria, por si, a definição das fronteiras de contexto.

## Consequências e custos

**Benefícios:**
- Responsabilidade explícita por dados e evoluções de schema.
- Menor risco de consultas e alterações indevidas entre contextos por credenciais de aplicação.
- Integração entre serviços por contratos verificáveis, sem depender de tabelas internas.
- Manutenção de transações locais às operações de cada serviço.

**Custos e limitações:**
- O cluster, o banco e a infraestrutura continuam compartilhados; há riscos comuns de indisponibilidade, recursos e administração.
- Integrações entre contextos exigem APIs/eventos, versionamento, observabilidade e tratamento de falhas.
- Consultas consolidadas são eventualmente consistentes e precisam de projeções; não há transações ACID distribuídas entre contextos.
- O proprietário do schema `credito_user` possui `CREATE` e controla os objetos próprios. Caso seja necessário restringir DDL em runtime, será preciso separar uma role de migração da role de aplicação.
- Alterações de permissões e credenciais devem ser reproduzíveis por scripts de infraestrutura e testadas nos ambientes de implantação, não apenas no Docker local.

## Quando não vale a pena

Este isolamento lógico pode ser insuficiente quando houver necessidade de segregação física ou regulatória estrita, janelas independentes de manutenção, escalabilidade e disponibilidade diferentes por serviço, ou políticas distintas de backup/restauração. Nessas condições, avaliar instâncias de banco separadas. Também pode ser excessivo para um sistema pequeno, com um único contexto e sem equipes ou ciclos de mudança independentes.

## Condição de falha

Reabrir a decisão se ocorrer **qualquer acesso não autorizado confirmado** entre schemas de negócio por credenciais de aplicação, se uma necessidade funcional exigir acesso SQL cruzado recorrente, ou se a operação do banco compartilhado causar **duas ou mais indisponibilidades** que afetem simultaneamente múltiplos serviços em **30 dias corridos**. Nestes casos, revisar privilégios, limites de contexto e a opção de instâncias independentes.

## Evidências e validação

Comandos executados no ambiente local durante a revisão:

```powershell
docker exec amp-postgres psql -U amp -d amp -c "\du"
docker exec amp-postgres psql -U amp -d amp -c "\dn+"
docker exec amp-postgres psql -U amp -d amp -c "SELECT nspname AS schema, has_schema_privilege('credito_user', oid, 'USAGE') AS pode_acessar, has_schema_privilege('credito_user', oid, 'CREATE') AS pode_criar FROM pg_namespace WHERE nspname IN ('credito', 'risco', 'antifraude', 'contratacao') ORDER BY nspname;"
docker exec amp-postgres psql -U amp -d amp -c "SELECT schemaname, tablename, tableowner FROM pg_tables WHERE schemaname = 'credito' ORDER BY tablename;"
docker exec amp-postgres psql -U amp -d amp -c "SELECT rolname, rolcanlogin, rolcreatedb, rolcreaterole, rolsuper FROM pg_roles WHERE rolname = 'credito_user';"
```

Resultados observados: `credito_user` com `USAGE` e `CREATE` apenas em `credito` entre os quatro schemas de negócio; seis tabelas pertencentes a essa role; `rolcanlogin = true`; `rolcreatedb = false`, `rolcreaterole = false`, `rolsuper = false`.

**Validação complementar recomendada antes do aceite:** conferir `CONNECT` no banco, privilégios diretos e herdados sobre tabelas e sequências dos demais schemas, `PUBLIC`, roles herdadas e credenciais efetivamente utilizadas pelo Spring Boot em implantação. Não inferir esses resultados a partir dos testes já executados.
