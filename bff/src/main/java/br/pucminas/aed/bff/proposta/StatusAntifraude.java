package br.pucminas.aed.bff.proposta;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.OffsetDateTime;

@JsonIgnoreProperties(ignoreUnknown = true)
public record StatusAntifraude(
    String solicitacaoId,
    Boolean aprovada,
    Integer score,
    String motivo,
    OffsetDateTime dataAnalise) {
}
