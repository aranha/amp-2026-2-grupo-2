package br.pucminas.aed.bff.proposta;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ParecerDeRisco(
    String solicitacaoId,
    Boolean elegivel,
    BigDecimal valorAprovado,
    String motivo,
    OffsetDateTime dataAnalise) {
}
