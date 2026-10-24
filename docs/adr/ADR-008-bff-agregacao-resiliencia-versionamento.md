# ADR-008 — BFF: agregação, resiliência e versionamento de API

## Status

Aceito

## Contexto

- **A tela precisa de três coisas ao mesmo tempo.** Para mostrar uma proposta, o `app-cliente`
  precisa da proposta (Crédito), do parecer (Risco) e do status da análise (Antifraude). São três
  donos diferentes, três schemas diferentes.
- **O navegador é o pior lugar para orquestrar.** Três chamadas significam três conexões, três
  tokens enviados, três tratamentos de erro e latência somada na rede do usuário, que é a mais
  lenta do caminho.
- **Os serviços de domínio não devem ser expostos.** Abrir os três na internet espalharia
  validação de JWT, CORS e política de erro por três bases de código em duas linguagens.
- **Os downstreams falham de formas diferentes.** Risco e Antifraude são acessórios: sem eles a
  proposta ainda tem valor na tela. Sem o Crédito não existe resposta nenhuma.
- **A forma da resposta vai mudar.** A v1 expõe `taxa` no topo; o produto quer agrupar as
  condições e acrescentar o CET. Remover um campo do topo quebra quem já consome.
- **O read model do Crédito já é desnormalizado.** A `proposta_view` recebe, por evento, dados de
  Risco e de Antifraude. Ler só ela seria mais simples — e é a alternativa mais séria contra esta
  decisão.

## Decisão

Um **BFF** na borda, único endereço que o `app-cliente` conhece, com três responsabilidades que
não são repasse: **agregar**, **resguardar** e **versionar**.

**Agregação.** `GET /v2/propostas/{id}` consulta as três fontes nos seus donos, ao vivo. O Crédito
vai primeiro — sem proposta não há o que agregar, e a resposta é 404 sem gastar as outras duas
chamadas. Risco e Antifraude saem **em paralelo**, em threads virtuais: sequencial, o pior caso
somaria os três timeouts.

**Resiliência.** Resilience4j, uma instância por downstream:

| Padrão | Configuração | Por quê |
|---|---|---|
| Timeout | 2s conexão / 3s leitura | nenhuma chamada pendura o BFF |
| Circuit breaker | janela 10, 50% de falha, abre 10s, 3 em half-open | para de bater em serviço caído e testa a volta sozinho |
| Retry | 3 tentativas, backoff exponencial, **só em GET** | GET é idempotente; repetir `POST /solicitacoes` criaria solicitação duplicada |
| Bulkhead | semáforo de 10 | Antifraude lento não consome o pool da consulta de proposta |

**Degradação assimétrica.** Risco fora do ar devolve `parecerIndisponivel: true`; Antifraude fora
devolve `antifraudeIndisponivel: true`; em ambos os casos o bloco correspondente some da resposta
e o resto é entregue. O **Crédito não degrada**: sem ele a resposta é **503** com a fonte no
corpo, porque devolver uma proposta sem proposta seria mentira em forma de JSON.

**404 não é falha.** Proposta inexistente é resposta do downstream, não indisponibilidade, e por
isso está em `ignoreExceptions` do circuit breaker e do retry. Sem isso, uma rajada de consultas a
IDs errados abriria o circuito de um serviço saudável.

**Versionamento por caminho.**

| Rota | Forma |
|---|---|
| `GET /v1/propostas/{id}` | campos planos: `valorAprovado`, `taxa`, `parcelas`, `status` |
| `GET /v2/propostas/{id}` | `condicoes { valorAprovado, taxa, parcelas, cet }`, sem `taxa` no topo |

As duas convivem e leem da **mesma agregação** — a v1 é uma projeção da v2, não um segundo
caminho de código. A v1 responde com `Deprecation`, `Sunset` e um `Link` `rel="successor-version"`
apontando para a sucessora.

### Antipadrões evitados

| Antipadrão | Risco | Como evitamos |
|---|---|---|
| **Camada decorativa** | um salto de rede que só repassa | o BFF agrega três fontes, degrada e versiona; nenhuma rota é proxy 1:1 |
| **Retry em operação não idempotente** | duplicar efeito no domínio | retry configurado só nos GET das três fontes |
| **Versionar por header ou query** | cache e log não distinguem versões | versão no caminho, visível em qualquer proxy |
| **Breaking change sem versão** | quebra o consumidor em produção | tirar `taxa` do topo criou a v2; a v1 continua respondendo |

## Alternativas consideradas

**A SPA chama os três serviços.** Sem salto extra e sem componente novo. Descartada: expõe três
serviços na internet, espalha validação de JWT e CORS por duas linguagens, soma latência na rede
do usuário e deixa cada cliente novo reimplementar a mesma orquestração.

**Ler só a `proposta_view` do Crédito.** O read model já tem elegibilidade e score projetados por
evento; uma chamada resolveria. Descartada por dois motivos. O primeiro é de contrato: aquele
projetado é **eventualmente consistente** e pertence ao Crédito — o BFF leria a cópia que o Crédito
fez do dado alheio, não o dado do dono. O segundo é de desenho: um BFF que repassa uma fonte é
exatamente a camada decorativa que esta arquitetura diz evitar. Se um dia a tela só precisar do
que a `proposta_view` tem, a resposta certa é a SPA consumir o Crédito direto, não manter um salto
inútil no meio.

**API Gateway de prateleira (Kong, Spring Cloud Gateway).** Roteamento, autenticação e rate limit
por configuração. Descartada porque gateway roteia, não agrega: a composição das três fontes e a
degradação parcial viram plugin ou script. Gateway e BFF resolvem problemas diferentes e, em
produção, coexistiriam.

**GraphQL na borda.** Resolveria agregação e over-fetching de uma vez. Descartada pelo custo em
relação ao tamanho do problema: são duas telas e uma consulta. GraphQL traria schema, resolvers,
limite de profundidade e cache por campo para resolver o que duas rotas REST resolvem — e
esconderia o eixo de versionamento que a disciplina pede demonstrar.

## Consequências e custos

**Melhorou**

- Uma chamada e um token por tela, em vez de três.
- Validação de JWT, CORS e política de erro num lugar só.
- Indisponibilidade de Risco ou de Antifraude vira resposta parcial, não erro: a tela continua
  útil com o que sobrou.
- As duas versões convivem sem duplicar a regra de agregação.

**Piorou**

- **Um salto de rede a mais** em toda leitura.
- **Ponto único de falha na borda.** BFF fora do ar é o sistema inteiro fora do ar para o usuário,
  mesmo com os quatro serviços saudáveis.
- **O BFF conhece o endereço dos três.** É acoplamento de borda assumido: evita o *distributed
  monolith* entre domínios, mas concentra na borda o conhecimento de quem é quem.
- **Latência de cauda piorada pelo fan-out.** A resposta é tão lenta quanto a mais lenta das
  chamadas paralelas; um p99 ruim no Antifraude aparece no p99 da tela.
- **Duas versões para manter**, com dois contratos, dois testes e uma data de desligamento que
  alguém precisa cumprir.
- **Dependência de contrato que ainda não existe.** `taxa`, `parcelas` e `cet` dependem de o
  Crédito expor as condições em `GET /v1/propostas/{id}`; hoje a `PropostaView` só tem
  `valorAprovado`. O BFF mapeia se vier e devolve nulo se não vier — o que significa que a
  diferença entre v1 e v2 só fica visível quando o Crédito expuser.

## Quando não vale a pena

- **Um cliente só, consumindo uma fonte só.** Aí o BFF é um salto de rede com nome bonito. Se a
  agregação cair para uma fonte, o certo é apagar o BFF, não mantê-lo por simetria.
- **Vários clientes com a mesma necessidade.** BFF se paga quando web, mobile e parceiro querem
  formas diferentes do mesmo dado. Querendo todos a mesma coisa, uma API pública bem desenhada
  resolve com um componente a menos.
- **Latência crítica de verdade.** Em caminho de altíssima frequência, o salto extra e o fan-out
  podem custar mais do que a conveniência do cliente.
- **Versionamento por caminho com muitas versões vivas.** Com v1, v2 e v3 simultâneas, o custo de
  manter rotas paralelas supera o de negociar conteúdo por content type.

## Condição de falha

Reabrimos esta decisão se:

- o BFF virar **gargalo de time** — toda mudança de tela exigindo alteração nele, com fila de
  pessoas esperando merge;
- a agregação cair para **uma fonte**, caso em que o BFF perde a razão de existir;
- a v1 continuar com tráfego relevante **depois da data de `Sunset`**, sinal de que o
  versionamento não está sendo cumprido e vira dívida permanente;
- o fan-out paralelo levar o **p99 da consulta acima de 2s**, caso em que a resposta passa a ser
  cache na borda ou leitura do read model, aceitando a consistência eventual.
