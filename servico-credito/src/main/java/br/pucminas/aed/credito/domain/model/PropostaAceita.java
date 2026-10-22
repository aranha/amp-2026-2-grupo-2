package br.pucminas.aed.credito.domain.model;

import java.time.OffsetDateTime;

/** Evento interno de domínio; a publicação Kafka cabe a um adaptador. */
public record PropostaAceita(String solicitacaoId, OffsetDateTime quando) {}
