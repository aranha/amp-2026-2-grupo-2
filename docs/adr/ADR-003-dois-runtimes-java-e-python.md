# ADR-003 — Dois runtimes: Java e Python, e o motivo de cada

## Status

Aceito

## Contexto

- **Dois serviços Java já existem.** Crédito e Risco vêm de Arquitetura Reativa e Event-Driven,
  com teste de domínio e envelope CloudEvents funcionando.
- **Antifraude é regra estatística sobre dados.** Score, limiar, distribuição — é o tipo de
  problema cujo ecossistema de mercado é Python.
- **O enunciado exige pelo menos duas linguagens**, com o motivo de cada.
- **O envelope é o mesmo para todos.** CloudEvents 1.0 em binary mode, headers `ce-*` no Kafka,
  definido no `asyncapi.yaml`. Se as duas linguagens não produzirem o mesmo envelope, o eixo de
  comunicação do trabalho inteiro cai.

## Decisão

| Runtime | Serviços | Motivo |
|---|---|---|
| **Java 21 / Spring Boot** | `servico-credito`, `servico-risco`, `bff` | já existem e já falam CloudEvents; o BFF precisa de Resource Server e Resilience4j maduros |
| **Python 3.12 / FastAPI** | `servico-antifraude`, `servico-contratacao` | análise estatística é terreno de Python; o segundo serviço prova que interoperar não foi exceção |

O contrato entre eles é o evento, nunca a linguagem: nenhum código, biblioteca ou modelo é
compartilhado entre os dois lados.

## Alternativas consideradas

**Tudo em Java.** Um toolchain, um CI, um jeito de instrumentar, uma linguagem no plantão.
Descartada porque o enunciado pede duas e porque jogaria fora o argumento real do Antifraude —
em Java, análise estatística significa reimplementar o que `pandas` e `scikit-learn` entregam.

**Tudo em Python.** Descartada porque obrigaria a reescrever dois serviços Java que já funcionam,
com teste, sem ganho de domínio nenhum. Reescrita sem motivo é desperdício, e o prazo é de três
sprints.

**Java mais Node/TypeScript no BFF.** É a combinação clássica de BFF, e o time tem familiaridade.
Descartada porque a segunda linguagem passaria a existir só na borda, onde o argumento de
ecossistema não se aplica — seria poliglota de demonstração, exatamente o que a régua da
disciplina chama de decorativo. E o BFF precisa de Resilience4j e Spring Security Resource Server,
que são o caminho mais curto para os itens de resiliência e validação de JWT.

**Kotlin nos serviços novos.** Mesma JVM, menos cerimônia. Descartada porque não conta como
segunda plataforma para o eixo de interoperabilidade: o bytecode e as bibliotecas são os mesmos.

## Consequências e custos

**Melhorou**

- O Antifraude usa o ecossistema certo para o problema que resolve.
- Os serviços herdados entram como estão, e o esforço vai para o domínio novo.
- A interoperabilidade deixa de ser slide e vira prova: o mesmo envelope CloudEvents produzido em
  Python é consumido em Java e vice-versa.

**Piorou**

- **Dois toolchains no CI.** Jobs separados de Maven e de pip, dois caches, dois jeitos de
  declarar dependência. O pipeline tem o dobro de superfície para quebrar — e quebra: hoje os
  dois jobs Python estão vermelhos enquanto os três Java passam.
- **Dois jeitos de instrumentar.** `opentelemetry-javaagent.jar` de um lado,
  `opentelemetry-instrument` do outro. Qualquer convenção de span precisa ser combinada duas vezes.
- **Nada é compartilhado.** O envelope CloudEvents é implementado duas vezes. Divergência só
  aparece em runtime, o que torna o teste de ida e volta Python ↔ Java uma dependência de Sprint 0,
  não um detalhe.
- **Plantão exige as duas.** Quem for depurar uma falha de ponta a ponta precisa ler Java e Python.
- **Dois conjuntos de atualizações de segurança** para acompanhar.

## Quando não vale a pena

- **Time de uma linguagem só.** A segunda vira custo de CI, de observabilidade e de plantão sem
  retorno. Poliglota se paga quando o ecossistema resolve o problema, não quando demonstra.
- **Quando a biblioteca decisiva existe nos dois lados.** Se o modelo de fraude fosse uma regra
  aritmética simples, Java resolveria e a segunda linguagem perderia o argumento.
- **Serviços que compartilham muito contrato.** Cada mudança de forma teria que ser escrita duas
  vezes, sem compilador para avisar. Nosso caso suporta porque o contato é um envelope estável e
  versionado.
- **Operação enxuta.** Um time pequeno cuidando de dois runtimes em produção gasta em manutenção
  o que ganhou em expressividade.

## Condição de falha

Reabrimos esta decisão se:

- o **envelope divergir entre os dois lados mais de uma vez por sprint**, sinal de que o contrato
  precisa de schema registry em vez de acordo em YAML;
- o tempo de pipeline passar de **10 minutos** por causa dos dois toolchains, a ponto de o time
  começar a pular o CI;
- o Antifraude deixar de usar biblioteca de análise de dados e virar regra aritmética — aí o
  motivo do Python some, e o serviço volta para a JVM.
