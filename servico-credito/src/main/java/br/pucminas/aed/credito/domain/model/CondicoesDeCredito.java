package br.pucminas.aed.credito.domain.model;

import java.math.BigDecimal;
import java.util.Objects;

/** Resultado imutável do cálculo. Taxa é uma fração ao mês (0.02 = 2% a.m.). */
public record CondicoesDeCredito(
        Dinheiro valorAprovado,
        BigDecimal taxaMensal,
        int numeroParcelas,
        Dinheiro valorParcela,
        Dinheiro valorTotal) {
    public CondicoesDeCredito {
        Objects.requireNonNull(valorAprovado);
        Objects.requireNonNull(taxaMensal);
        Objects.requireNonNull(valorParcela);
        Objects.requireNonNull(valorTotal);
        if (taxaMensal.signum() < 0) throw new IllegalArgumentException("taxa mensal negativa");
        if (numeroParcelas < 1) throw new IllegalArgumentException("numero de parcelas invalido");
    }
}
