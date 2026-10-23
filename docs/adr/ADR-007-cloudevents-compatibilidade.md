# ADR-007 — CloudEvents e compatibilidade de contratos

- **Status:** proposta da trilha de Contratação
- **Dono:** Marcus Santos
- **Data:** 2026-10-22

## Contexto

O serviço de Contratação consome `credito.proposta.v1` e publica
`contratacao.contrato.v1` e `contratacao.liberacao.v1`. Os eventos precisam ser
consumidos por serviços Java e Python sem acoplamento ao framework de origem.

O contrato compartilhado usa CloudEvents 1.0 em binary mode. O identificador da
solicitação permanece como chave de partição Kafka para preservar a ordem por
solicitação.

## Decisão proposta

1. `ce_specversion`, `ce_id`, `ce_source`, `ce_type`, `ce_time` e
   `ce_datacontenttype` são obrigatórios em todos os eventos.
2. `ce_type` identifica a versão do evento, por exemplo
   `contratacao.contrato.v1`.
3. Mudanças compatíveis são aditivas: novos campos opcionais no payload ou novos
   consumidores não alteram o tópico existente.
4. Remover ou renomear campo obrigatório, mudar seu tipo, alterar sua semântica
   ou mudar a chave de partição é breaking change e cria um novo tópico `.v2`.
5. O consumidor deve ignorar campos desconhecidos e tratar `ce_id` como
   identificador de idempotência.
6. A liberação do crédito tenta o core no máximo três vezes. As duas falhas
   iniciais deixam o estado em `RETRYING`; a terceira move a mensagem para a
   DLQ e interrompe a retentativa automática. O limite é uma proposta até o
   aceite do grupo e do contrato do core.

## Consequências

- Produtores podem adicionar campos sem coordenar uma troca simultânea de todos
  os consumidores.
- Mudanças incompatíveis exigem operar v1 e v2 em paralelo durante a migração.
- Idempotência precisa ser persistida pelo consumidor antes de confirmar a
  mensagem.
- O limite de três tentativas evita retry infinito, mas pode enviar falhas
  transitórias para a DLQ; o reprocessamento será manual e observável.
- A decisão de payload ainda depende do aceite do `asyncapi.yaml` pelos donos
  de Crédito, Antifraude e Risco.

## Quando não vale a pena

Se houver apenas um consumidor controlado pelo mesmo time e nenhuma evolução
independente do contrato, manter um contrato interno simples pode ser mais barato
que versionar tópicos. Aqui a compatibilidade explícita é necessária porque os
consumidores usam linguagens e ciclos de entrega diferentes.
