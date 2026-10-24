package br.pucminas.aed.bff.proposta;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PropostaDoCredito(
    String solicitacaoId,
    String propostaId,
    Boolean elegivel,
    BigDecimal valorAprovado,
    String statusContrato,
    String contratoId,
    OffsetDateTime atualizadaEm,
    CondicoesDoCredito condicoes) {
}
