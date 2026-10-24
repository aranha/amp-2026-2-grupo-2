package br.pucminas.aed.bff.solicitacao;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SolicitacaoRecebida(
    String eventoId,
    String solicitacaoId,
    String clienteId,
    BigDecimal valorSolicitado,
    OffsetDateTime dataSolicitacao,
    String canalOrigem) {
}
