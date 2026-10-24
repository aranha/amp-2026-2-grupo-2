package br.pucminas.aed.bff.proposta;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CondicoesDoCredito(
    BigDecimal taxaMensal,
    Integer numeroParcelas,
    BigDecimal valorParcela,
    BigDecimal valorTotal,
    BigDecimal cet) {
}
