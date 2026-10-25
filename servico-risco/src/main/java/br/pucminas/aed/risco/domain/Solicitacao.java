package br.pucminas.aed.risco.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * O pedido de credito como o contexto de Risco o enxerga: so o que a analise usa. Nao e o
 * agregado do servico-credito, e a copia que chegou pelo evento.
 */
public record Solicitacao(UUID id, String clienteId, BigDecimal valorSolicitado, OffsetDateTime solicitadaEm) {

    public Solicitacao {
        if (id == null) {
            throw new SolicitacaoInvalidaException("id da solicitacao e obrigatorio");
        }
        if (clienteId == null || clienteId.isBlank()) {
            throw new SolicitacaoInvalidaException("clienteId e obrigatorio");
        }
        if (valorSolicitado == null || valorSolicitado.signum() <= 0) {
            throw new SolicitacaoInvalidaException("valorSolicitado deve ser positivo");
        }
        if (valorSolicitado.stripTrailingZeros().scale() > 2) {
            throw new SolicitacaoInvalidaException("valorSolicitado tem no maximo duas casas decimais");
        }
        if (solicitadaEm == null) {
            throw new SolicitacaoInvalidaException("dataSolicitacao e obrigatoria");
        }
        valorSolicitado = valorSolicitado.setScale(2, RoundingMode.UNNECESSARY);
    }
}
