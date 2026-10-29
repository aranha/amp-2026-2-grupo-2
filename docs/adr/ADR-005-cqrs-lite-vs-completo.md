# ADR-005 — CQRS Lite versus CQRS completo no serviço de crédito

## Status

**Aceito** — sujeito à revisão e aceite do grupo no pull request.

## Contexto

O sistema de concessão de crédito precisa preservar as invariantes das operações de escrita e, ao mesmo tempo, responder a uma consulta consolidada de proposta com informações que são produzidas por outros contextos: Risco, Antifraude e Contratação. Essas informações chegam assincronamente pelos tópicos Kafka `risco.elegibilidade.v1`, `antifraude.analise.v1` e `contratacao.contrato.v1`.

Uma consulta que reconstruísse o resultado realizando chamadas síncronas aos três serviços criaria dependência de disponibilidade e latência das fontes. Uma consulta direta ao agregado de escrita também não reuniria necessariamente os dados emitidos por esses contextos. O trabalho exige que `GET /v1/propostas/{id}` leia **somente** `credito.proposta_view`.

Na implementação da trilha de Crédito, o lado de comandos usa o domínio `SolicitacaoDeCredito` e a tabela `credito.solicitacao`; o lado de consulta utiliza a tabela desnormalizada `credito.proposta_view`, mantida pelo consumidor Kafka. As duas estruturas pertencem ao mesmo schema `credito` e à mesma instância PostgreSQL do ambiente acadêmico. Não há infraestrutura física separada de leitura, barramento exclusivo do modelo de leitura, nem armazenamento de todos os eventos como fonte primária do estado.

## Decisão

**Adotar CQRS Lite no serviço de crédito:** separar responsabilidades de escrita e leitura com modelos e acessos distintos, mantendo ambos no mesmo serviço e no mesmo PostgreSQL, sob o schema `credito`. A consulta `GET /v1/propostas/{id}` acessará exclusivamente a projeção `credito.proposta_view`, atualizada assincronamente pelos eventos Kafka dos demais contextos.

Essa decisão **não** implica Event Sourcing: o estado de escrita permanece persistido em tabelas relacionais, e o histórico completo de eventos não é a fonte oficial para reconstrução dos agregados.

### Funcionamento adotado

1. **Comandos e invariantes:** a aplicação usa `SolicitacaoDeCredito` e sua persistência; a tentativa de aceitar uma proposta já aceita deve gerar conflito HTTP `409`.
2. **Entrada de eventos:** `PropostaProjectionListener` consome os três tópicos no grupo `credito-proposta-view-v1` e delega a `PropostaProjectionService`.
3. **Projeção transacional:** `PropostaProjectionService` registra `(topico, evento_id)` em `credito.proposta_eventos_processados` e atualiza `credito.proposta_view` na mesma transação. Eventos repetidos com a mesma chave não reaplicam o efeito.
4. **Eventos fora de ordem:** cada conjunto de campos (risco, antifraude, contrato) só é substituído quando a data do novo evento não é anterior à data já projetada para aquele conjunto.
5. **Consulta:** `ConsultaPropostaController`/repositório de leitura obtêm a resposta do modelo desnormalizado, sem ler `credito.solicitacao` no endpoint GET.

As garantias de deduplicação se limitam à combinação `(topico, evento_id)`. Não se presume ordenação total entre tópicos. Empates de data, correções retroativas e evolução de schema exigem uma política explícita se se tornarem requisitos.

## Alternativas consideradas

1. **Sem CQRS: leitura diretamente das tabelas de escrita ou composição síncrona de APIs.** Descartada porque não oferece o modelo consolidado exigido e/ou acopla a disponibilidade da consulta aos serviços de Risco, Antifraude e Contratação.
2. **CQRS completo: componentes, bancos e escalabilidade independentes para comandos e consultas.** Adiado porque traz infraestrutura e operação adicionais (deploy, monitoramento, segurança, backup e sincronização). O volume e os requisitos de desempenho disponíveis para este projeto acadêmico não demonstram necessidade dessa separação física.
3. **Event Sourcing integral com reconstrução do agregado por replay.** Não adotado porque o requisito é uma projeção por eventos de integração, não preservar todos os eventos de domínio como fonte primária. Introduzir event store, versionamento histórico e replay completo aumentaria a complexidade sem benefício comprovado no escopo atual.

## Consequências e custos

**Benefícios**

- Contratos claros para comandos e consultas; invariantes permanecem no domínio de escrita.
- Resposta consolidada sem chamadas síncronas aos demais contextos no momento do GET.
- Leitura simples de uma tabela desnormalizada; evolução e testes de projeções independentes das regras do agregado.
- Reprocessamentos duplicados não reaplicam alterações quando preservam `topico` e `eventoId`.
- Menor esforço operacional do que dois serviços e bancos fisicamente separados.

**Custos e limitações**

- **Consistência eventual:** uma consulta pode retornar `404` ou informações incompletas enquanto os eventos ainda não chegaram ou não foram processados. `HTTP 200` não garante que todos os três contextos já tenham produzido eventos.
- A mesma instância de PostgreSQL sustenta comandos e consultas; um problema nela pode afetar ambos os lados.
- É necessário operar consumidores, offsets, tentativas de processamento, observabilidade, DLQ e procedimentos de reconstrução da projeção.
- A tabela `proposta_eventos_processados` precisa de estratégia de retenção compatível com a janela de reentrega dos eventos; não se deve limpá-la arbitrariamente.
- O contrato dos eventos precisa ser compatível com os campos que o consumidor interpreta. A implementação testada espera campos no corpo JSON, como `solicitacaoId` e `eventoId`; a conformidade integral com os headers CloudEvents do AsyncAPI ainda demanda validação entre equipes.
- A lógica de projeção não substitui mecanismos de integridade e concorrência no modelo de escrita.

## Quando não vale a pena

Essa abordagem seria inadequada se o negócio exigisse leitura estritamente consistente após cada comando, se dados de vários serviços tivessem de ser confirmados de modo atômico, ou se a carga de leitura exigisse armazenamento e escalabilidade fisicamente independentes. Se o sistema fosse pequeno e não tivesse necessidade de consulta consolidada por eventos, uma única modelagem de escrita e leitura seria mais simples.

## Condição de falha

Reabrir a decisão se, **durante 30 dias consecutivos em ambiente monitorado**, qualquer uma destas condições for confirmada:

- **p95 de atraso de atualização da projeção superior a 10 segundos** entre a publicação do evento e sua disponibilidade no `GET`, sob carga representativa e com correlação por identificador; ou
- **mais de 1% dos eventos destinados à projeção precisarem de intervenção manual**, medidos pelo total publicado no período; ou
- o banco compartilhado se mostrar o principal limitador de capacidade de leitura, com **p95 do GET acima de 500 ms** sob carga acordada pelo grupo, após otimizações básicas de consulta e índices.

Esses valores são **gatilhos de reavaliação propostos**, não SLOs já aprovados nem métricas medidas no projeto. Se ocorrerem, avaliar materialização/reconstrução mais robusta, separação física do modelo de leitura e capacidade operacional para CQRS completo.

## Evidências de implementação e validação

- `servico-credito/src/main/java/br/pucminas/aed/credito/adapter/in/PropostaProjectionListener.java` — listener dos três tópicos Kafka.
- `servico-credito/src/main/java/br/pucminas/aed/credito/cqrs/PropostaProjectionService.java` — gravação transacional da deduplicação e da projeção; proteção por data do evento.
- `servico-credito/src/main/java/br/pucminas/aed/credito/adapter/in/ConsultaPropostaController.java` e `adapter/out/JdbcPropostaViewRepository.java` — API e acesso ao modelo de leitura.
- `servico-credito/src/main/resources/schema-credito-cqrs.sql` — estrutura das duas tabelas de CQRS.
- Evidências reportadas na validação local de 22/10/2026: **oito testes direcionados aprovados**; evento de risco refletido em `credito.proposta_view` com `valor_aprovado = 5000.00`; reenvio com o mesmo `eventoId` mantendo um único registro de deduplicação; evento de risco mais antigo não sobrescrevendo o resultado; `GET /v1/propostas/teste-cqrs-001` retornando HTTP `200` com campos de risco e antifraude.

**Validações complementares antes da aprovação final:** revisar o JSON integral de contratação; conferir o consumo real de eventos publicados pelos serviços Python; executar testes automatizados de integração com Kafka/PostgreSQL, falhas e reentrega; verificar compatibilidade do contrato AsyncAPI/CloudEvents; testar que o GET não consulta o modelo de escrita e registrar medições de latência/lag. Os testes executados não demonstram, por si sós, essas garantias adicionais.
