package br.pucminas.aed.bff.analise;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AnaliseResumo(
    String solicitacaoId,
    boolean elegivel,
    BigDecimal valorAprovado,
    String motivo,
    Instant dataAnalise) {
}
