package br.pucminas.aed.bff.analise;

import java.math.BigDecimal;
import java.time.Instant;

public record AnaliseResumo(
    String solicitacaoId,
    boolean elegivel,
    BigDecimal valorAprovado,
    String motivo,
    Instant dataAnalise) {
}
