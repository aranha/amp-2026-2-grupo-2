package br.pucminas.aed.bff.proposta;

import java.math.BigDecimal;

public record PropostaV1(
    String solicitacaoId,
    BigDecimal valorAprovado,
    BigDecimal taxa,
    Integer parcelas,
    String status) {

    public static PropostaV1 de(PropostaV2 v2) {
        CondicoesV2 condicoes = v2.condicoes();
        return new PropostaV1(
            v2.solicitacaoId(),
            condicoes == null ? null : condicoes.valorAprovado(),
            condicoes == null ? null : condicoes.taxa(),
            condicoes == null ? null : condicoes.parcelas(),
            v2.statusContrato());
    }
}
