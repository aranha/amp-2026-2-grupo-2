package br.pucminas.aed.bff.proposta;

import java.math.BigDecimal;

public record CondicoesV2(
    BigDecimal valorAprovado,
    BigDecimal taxa,
    Integer parcelas,
    BigDecimal cet) {
}
