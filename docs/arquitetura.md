# Arquitetura do sistema de concessão de crédito

> **Esqueleto.** Consolidação final por Paulo, até 01/11. Cada seção puxa de uma ADR e de um
> bloco da apresentação — o documento não cita a disciplina nem a aula.
> Conteúdo de origem em [plano-de-trabalho.md](plano-de-trabalho.md).

## 1. O domínio e o problema
<!-- Por que partes do sistema têm necessidades diferentes: leitura rápida, consistência,
     baixa latência, alta disponibilidade. Checklist B1. -->

## 2. Por que multiplataforma
<!-- Trade-off explícito contra o monolito. Dois runtimes e o motivo de cada. ADR-001, ADR-003. -->

## 3. Os três eixos
<!-- Comunicação (síncrona e assíncrona) · Dados (Database per Service) ·
     Implantação (containers e orquestração). Checklist B4-B6. -->

## 4. O desenho — C4
<!-- N1 Contexto · N2 Container · N3 Componente do BFF e do crédito. Checklist B7-B9. -->

## 5. Padrões internos
<!-- Clean/Hexagonal e a equivalência · DDD tático · CQRS · Observer → Kafka.
     ADR-002, 004, 005, 006. -->

## 6. Comunicação, contratos e resiliência
<!-- Tópicos, consumer groups, consistência eventual · BFF e as 3 fontes ·
     circuit breaker, retry só em GET, timeout, bulkhead · versionamento. ADR-007, 008. -->

## 7. Segurança
<!-- IdP, OAuth2/OIDC, PKCE, client credentials, validação do JWT, 401 vs 403. ADR-009. -->

## 8. Observabilidade e operação
<!-- Três sinais, trace atravessando a fila, 5 dashboards, Docker, K8s, CI/CD. ADR-010, 011. -->

## 9. Antipadrões evitados
<!-- Distributed monolith · fragmentação excessiva · camada decorativa · password grant. -->

## 10. O que ficou de fora, e por quê
<!-- A régua da disciplina: "quando não vale a pena". Vira o bloco 7 da apresentação. -->
