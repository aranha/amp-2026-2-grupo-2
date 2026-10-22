package br.pucminas.aed.credito.cqrs;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** Read model: sem dependencias do agregado ou tabela de escrita. */
public record PropostaView(String solicitacaoId, Boolean elegivel, BigDecimal valorAprovado,
    OffsetDateTime elegibilidadeEm, Boolean antifraudeAprovada, Integer scoreAntifraude,
    OffsetDateTime antifraudeEm, String contratoId, String propostaId,
    String statusContrato, OffsetDateTime contratoEm, OffsetDateTime atualizadaEm) {}
