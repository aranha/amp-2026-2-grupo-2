# ADR-001 — Multiplataforma em vez de monolito

## Status

Aceito

## Contexto

- **Cinco fronteiras no domínio.** O EventStorming de concessão de crédito identificou Crédito,
  Risco, Antifraude, Contratação e Liberação. Cada uma tem vocabulário e dono de regra próprios.
- **As partes não têm a mesma necessidade.** O Antifraude é CPU-bound e tolera latência — ninguém
  espera a análise na tela. A consulta de proposta precisa de leitura rápida e alta
  disponibilidade. A Liberação precisa de consistência forte com o core bancário.
- **Volume estimado: ~1.000 solicitações/dia.** Número do diário de eventos da disciplina
  anterior. É baixo.
- **Dois serviços Java já existem**, com teste e com CloudEvents, herdados de Arquitetura Reativa
  e Event-Driven.
- **O enunciado pede** no mínimo 3 a 4 serviços de domínio mais um BFF, em pelo menos duas
  linguagens, cada um com seu schema.

## Decisão

Quatro serviços de domínio — Crédito, Risco, Antifraude, Contratação+Liberação — mais um BFF na
borda. Entre serviços de domínio, **nenhuma chamada síncrona**: só eventos no Kafka. Síncrono
existe apenas de `app-cliente` para o BFF e do BFF para os serviços.

Contratação e Liberação ficam **no mesmo serviço**. As duas etapas mudam sempre pelo mesmo motivo
— regra de formalização — e separá-las criaria um serviço de duas tabelas sem dono de regra
próprio.

## Alternativas consideradas

**Monolito modular, um processo só.** Módulos com fronteira interna respeitada, um banco com
schemas separados, um deploy. Descartada **para este trabalho** porque o eixo de implantação
independente é o que a disciplina cobra demonstrar, e porque num processo único o pico de CPU do
Antifraude degrada a consulta de proposta — as duas competem pelo mesmo pool de threads. Em
termos de volume, porém, essa seria a escolha certa: ver *Quando não vale a pena*.

**Monolito mais um worker só para o Antifraude.** Resolveria o problema real de isolamento de
carga com um processo a mais, em vez de quatro. Descartada porque não exercita fronteira de
contexto, Database per Service nem contrato entre times — e porque deixaria o trabalho com um
serviço de domínio, abaixo do mínimo do enunciado.

**Um serviço por fronteira do EventStorming — cinco serviços.** Descartada por **fragmentação
excessiva**: Contratação e Liberação não têm ciclo de mudança independente. Separar aumentaria o
número de deploys e de saltos de rede sem reduzir acoplamento, que é a definição do antipadrão.

## Consequências e custos

**Melhorou**

- O Antifraude escala e cai sozinho. Um pico de análise não derruba a consulta de proposta.
- Cada contexto tem schema e usuário próprios: um `SELECT` cruzando fronteira falha com
  `permission denied`, e isso é demonstrável ao vivo.
- Cada serviço escolhe seu runtime (ADR-003) e seu ritmo de release.
- Nenhum serviço de domínio conhece o endereço de outro, o que mantém o *distributed monolith*
  fora do desenho.

**Piorou**

- **Consistência eventual vira regra, não exceção.** A proposta aparece na tela antes de o parecer
  de risco chegar. Toda leitura precisa saber lidar com dado ainda não projetado.
- **Cinco processos, um broker, um IdP e um banco para subir** antes de ver qualquer tela.
- **Depurar exige correlação.** Sem trace distribuído, "a solicitação sumiu" não tem resposta —
  por isso observabilidade deixou de ser opcional e virou trilha (ADR-010).
- **Falha parcial é o estado normal.** A borda precisa de circuit breaker, timeout e fallback
  (ADR-008) para não transformar indisponibilidade de um serviço em erro para o usuário.
- **Custo de coordenação entre pessoas.** Contrato de evento combinado antes do código, e um
  prazo rígido para o `asyncapi.yaml`, porque quem publica trava quem consome.

## Quando não vale a pena

- **No nosso volume.** Com ~1.000 solicitações/dia, um monolito modular resolveria com uma fração
  da operação. Isso vai dito na apresentação: escolhemos multiplataforma porque a disciplina cobra
  o eixo de implantação independente, e porque o isolamento do Antifraude é um ganho real — não
  porque a escala exija.
- **Time pequeno sem especialização.** Microsserviço se paga quando times diferentes liberam em
  ritmos diferentes. Com um time só, o custo de coordenação aparece e o benefício não.
- **Domínio ainda instável.** Fronteira errada em monolito se corrige refatorando; em serviço
  separado, exige migrar banco, versionar contrato e coordenar deploy.
- **Transação única de verdade.** Se o negócio exigisse atomicidade entre Crédito e Contratação,
  a saga custaria mais do que uma transação de banco em processo único.

## Condição de falha

Reabrimos esta decisão se:

- o grupo gastar **mais tempo em infraestrutura do que em regra de domínio** por duas sprints
  seguidas — sinal de que a arquitetura está cobrando mais do que devolve;
- alguma regra de negócio exigir **atomicidade entre dois contextos**, tornando a compensação da
  saga mais complexa que a própria regra;
- o volume ficar **abaixo de 100 solicitações/dia** em produção, caso em que o custo operacional
  de quatro serviços não se justifica.
