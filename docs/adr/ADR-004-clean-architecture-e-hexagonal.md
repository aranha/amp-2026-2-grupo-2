# ADR-004 — Clean Architecture e Hexagonal: a mesma regra de dependência

## Status

Proposto — aceite no PR da `trilha/risco`.

## Contexto

- **O enunciado cobra as duas coisas.** Clean Architecture **ou** Hexagonal, com a regra de
  dependência demonstrada (checklist B17), e a **equivalência** entre as duas explicada (B18).
- **Os dois serviços Java herdados da AED eram em camadas por tipo técnico:** `controller/`,
  `service/`, `domain/`. No `servico-risco`, o `domain/` eram três classes de evento com anotação
  Jackson; o `service/` gravava direto com `JdbcTemplate`; e a única regra de negócio que existia —
  as transições da saga de reserva, que só podem avançar — morava num
  `UPDATE ... WHERE status IN (...)`.
- **A regra herdada só era testável com infraestrutura.** Dos 24 testes do risco, 17 subiam Kafka
  embarcado e H2 — entre eles todos os da saga; os outros 7 cobriam só o volume por janela, que
  não decide nada.
- **O crédito já foi refatorado em Clean** (`domain/model`, `application`, `adapter/in|out`), com
  o DDD tático da trilha do Vinicius.
- **O risco é feito de bordas.** Três tópicos e uma rota HTTP chegam; Postgres e um tópico de saída
  saem. Pouca regra, muitas tecnologias em volta dela.

## Decisão

**Hexagonal no `servico-risco`, Clean no `servico-credito`, e a mesma regra nos dois:** toda
dependência de código-fonte aponta para dentro, para o domínio. O domínio não conhece framework,
broker nem banco; quem conhece são os adaptadores, e eles dependem do núcleo, nunca o contrário.

### A equivalência

As duas são nomes diferentes para a mesma inversão de dependência: a interface pertence a quem a
usa — o núcleo —, e a implementação fica do lado de fora.

| Clean Architecture (Martin) | Hexagonal (Cockburn) | No risco | No crédito |
|---|---|---|---|
| Entities | o centro do hexágono | `domain/` — `AnaliseDeCredito` | `domain/model` — `SolicitacaoDeCredito` |
| Use Cases (Interactors) | serviços de aplicação | `application/*Service` | `application/AceitarPropostaUseCase` |
| Input Boundary | porta primária (*driving*) | `application/port/in` | o próprio caso de uso, sem interface |
| Output Boundary / Gateway | porta secundária (*driven*) | `application/port/out` | `PersistenciaProposta` |
| Interface Adapters | adaptadores | `adapter/in/*` · `adapter/out/*` | `adapter/in` · `adapter/out` |
| Frameworks & Drivers | os atores lá fora | Spring, Kafka, Postgres | os mesmos |
| *Main component* | configurador | `adapter/config/CasosDeUsoConfig` | `CreditoConfig` |

O que muda é o desenho e a ênfase. Clean desenha círculos concêntricos e fala em camadas; tem
nome para o que sai para a tela (*presenters*, *view models*). Hexagonal desenha um dentro e um
fora e fala em **portas como intenção** — o que a aplicação oferece, o que ela precisa — sem
hierarquia entre os adaptadores: Kafka, HTTP e teste entram pelo mesmo tipo de porta.

### Por que Hexagonal justamente no risco

Porque é o serviço em que o vocabulário de portas descreve melhor o que existe: quatro entradas
e duas saídas em volta de um agregado pequeno. E porque mostrar a equivalência pede os dois
vocabulários no mesmo sistema, cada um onde ele é mais natural — a tabela acima é a prova.

### Como foi aplicado

1. **Domínio só com o JDK.** `AnaliseDeCredito` aplica a regra de elegibilidade na criação e
   protege as transições da saga; a regra saiu do SQL e virou método do agregado.
2. **Quatro portas de entrada** (`AvaliarSolicitacao`, `AcompanharReserva`, `ConsultarAnalises`,
   `AgregarFluxoDeSolicitacoes`) e **três de saída** (`AnalisesDeCredito`, `EventosProcessados`,
   `PublicadorDeElegibilidade`), todas em `application/port`.
3. **Casos de uso sem Spring.** Nenhuma anotação: são instanciados em `CasosDeUsoConfig`, que
   também aplica a transação de fora, com um `TransactionInterceptor` em volta da porta. O caso de
   uso não sabe que a transação existe.
4. **Adaptadores só traduzem.** O listener lê `ce_id` e o JSON e chama a porta; o publicador monta
   o envelope CloudEvents; o JDBC mapeia linha para agregado. Nenhuma regra mora neles.
5. **A regra é verificada no build.** `RegraDeDependenciaTest` (ArchUnit) exige que o domínio só
   dependa de `java..`, que a aplicação só dependa do domínio e de `org.slf4j`, e — com
   `onionArchitecture()` — que as setas apontem para dentro e nenhum adaptador conheça outro.
   Testado ao contrário: uma classe no domínio anotada com `@Component` e apontando para o
   adaptador JDBC derruba duas das três regras.

## Alternativas consideradas

**Manter as camadas herdadas.** `controller/service/repository` funciona, tem teste e é o que a
maioria dos projetos Spring faz. Descartada porque a regra continuaria no SQL e nos services que
importam `JdbcTemplate`: não há como testá-la sem broker e banco, e não há regra de dependência a
demonstrar — os serviços dependem da infraestrutura diretamente.

**Clean também no risco.** Os dois serviços Java ficariam iguais, e quem sabe um sabe o outro.
Descartada porque o item B18 pede a **equivalência**: dois vocabulários aplicados ao mesmo
princípio mostram que a regra é uma só; dois serviços iguais mostram só que copiamos a estrutura.

**Hexagonal com `@Service` e `@Transactional` nos casos de uso.** Mais curto: dispensa o
`CasosDeUsoConfig` e o proxy. Descartada porque deixa o núcleo dependente de Spring, e a ArchUnit
teria de abrir exceção para isso — exatamente o momento em que a regra vira sugestão.

**Um módulo Maven por camada.** O compilador imporia a regra, sem ArchUnit. Descartada pelo
custo: três ou quatro POMs, build multi-módulo no Dockerfile e no CI, para um serviço de 38
classes. Um teste de arquitetura dá a mesma garantia com um arquivo.

## Consequências e custos

**Melhorou**

- **A regra roda sozinha:** 19 casos de domínio em 34 ms, sem Spring, Kafka ou banco; os casos de
  uso são testados com as portas em memória. Antes, testar a saga exigia broker e banco no ar.
- **A regra ficou explícita.** Elegibilidade e transições da saga estão no agregado, com nome;
  antes, a única regra era um `WHERE status IN (...)`.
- **Trocar uma borda não toca o núcleo.** Os testes de caso de uso já fazem isso — trocam
  Postgres e Kafka por memória. Assinar o tópico novo e o herdado ao mesmo tempo foi mudança de
  propriedade de um adaptador.
- **A fronteira é conferida pelo build**, não por code review.

**Piorou**

- **Mais arquivos e mais saltos.** 15 classes de produção viraram 38. Seguir uma solicitação do
  Kafka até o banco passa por listener → porta → caso de uso → agregado → porta → adaptador.
- **Mapeamento repetido.** O mesmo dado existe como JSON no adaptador Kafka, `Solicitacao` no
  domínio, linha no JDBC e `AnaliseResposta` na web. Campo novo atravessa quatro arquivos.
- **Transação menos visível.** Quem procura `@Transactional` não acha nada; o começo da transação
  está no `CasosDeUsoConfig`, e é preciso saber disso.
- **Interfaces com uma implementação só.** As portas de entrada têm um único implementador em
  produção; a segunda implementação só existe nos testes. Até alguém precisar dela, parece
  cerimônia.
- **O hexágono não dispensa teste de integração.** Ele não prova que o SQL roda nem que o envelope
  bate com o contrato — por isso `JdbcAnalisesDeCreditoTest`, os testes de contrato contra o
  `asyncapi.yaml` e os de Kafka embarcado continuam.

## Quando não vale a pena

- **CRUD sem regra.** Se o serviço só grava e lê, porta e adaptador viram camada decorativa: cada
  porta repassa para um adaptador que repassa para o banco. Controller e repository bastam.
- **Componente sem domínio.** Por isso o **BFF não é hexagonal**, de propósito: ele agrega,
  resguarda e versiona, mas não tem regra de negócio a proteger. Lá, portas seriam a camada
  decorativa que a ADR-008 diz evitar.
- **Quando o framework é o domínio.** Um pipeline de dados escrito na API do pandas ou do Spark
  tem a regra na própria transformação; isolá-la é lutar contra a ferramenta.
- **Sem quem cuide da fronteira.** Sem um teste de arquitetura, o hexágono degrada em camadas com
  nomes bonitos em poucas sprints.

## Condição de falha

Reabrimos esta decisão se:

- a ArchUnit precisar de **exceção** para aceitar um import de framework no domínio ou na
  aplicação — sinal de que a regra deixou de valer;
- mais da metade das mudanças no risco numa sprint precisar tocar **quatro camadas** para um campo
  novo — o custo do mapeamento passou do benefício;
- a elegibilidade **sair do agregado**, por exemplo para uma consulta a um bureau de crédito: o
  domínio ficaria vazio, e o serviço voltaria a ser bordas em volta de nada.

## Evidências

- Estrutura: `servico-risco/src/main/java/br/pucminas/aed/risco/{domain,application,adapter}`
- Regra de dependência: `servico-risco/src/test/java/br/pucminas/aed/risco/RegraDeDependenciaTest.java`
- Domínio sem framework: `servico-risco/src/test/java/br/pucminas/aed/risco/domain/AnaliseDeCreditoTest.java`
- Casos de uso com portas em memória: `servico-risco/src/test/java/br/pucminas/aed/risco/application/`
- Validação de 25/10/2026: 83 testes verdes em três execuções seguidas; no compose, o parecer
  publicado pelo risco foi consumido pelo antifraude (Python) e pela `proposta_view` do crédito.
