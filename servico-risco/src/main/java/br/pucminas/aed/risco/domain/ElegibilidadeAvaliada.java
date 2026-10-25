package br.pucminas.aed.risco.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Domain Event: o fato que Credito e Antifraude consomem em {@code risco.elegibilidade.v1}. */
public record ElegibilidadeAvaliada(
        UUID eventoId,
        UUID solicitacaoId,
        boolean elegivel,
        BigDecimal valorAprovado,
        String motivo,
        OffsetDateTime dataAnalise) {
}
