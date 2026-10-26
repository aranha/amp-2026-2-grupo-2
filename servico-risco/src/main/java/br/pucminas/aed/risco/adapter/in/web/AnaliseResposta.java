package br.pucminas.aed.risco.adapter.in.web;

import br.pucminas.aed.risco.domain.AnaliseDeCredito;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * O parecer como sai na API. Os cinco primeiros campos espelham a carga de
 * {@code risco.elegibilidade.v1}; o {@code clienteId} fica de fora, porque nenhum consumidor
 * precisa dele.
 */
public record AnaliseResposta(
        String solicitacaoId,
        boolean elegivel,
        BigDecimal valorAprovado,
        String motivo,
        OffsetDateTime dataAnalise,
        BigDecimal valorSolicitado,
        String situacaoDaReserva) {

    static AnaliseResposta de(AnaliseDeCredito analise) {
        return new AnaliseResposta(
                analise.solicitacaoId().toString(),
                analise.parecer().elegivel(),
                analise.parecer().valorAprovado(),
                analise.parecer().motivo(),
                analise.analisadaEm(),
                analise.solicitacao().valorSolicitado(),
                analise.situacaoDaReserva().name());
    }
}
