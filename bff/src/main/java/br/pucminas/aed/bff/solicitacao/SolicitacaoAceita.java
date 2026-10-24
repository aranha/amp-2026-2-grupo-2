package br.pucminas.aed.bff.solicitacao;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record SolicitacaoAceita(
    String solicitacaoId,
    String eventoId,
    BigDecimal valorSolicitado,
    OffsetDateTime dataSolicitacao) {
}
