package br.pucminas.aed.credito.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/** Valor monetário em reais, sempre positivo para propostas de crédito. */
public record Dinheiro(BigDecimal valor) {
    public Dinheiro {
        Objects.requireNonNull(valor, "valor é obrigatório");
        if (valor.signum() <= 0) throw new IllegalArgumentException("valor deve ser positivo");
        if (valor.scale() > 2 && valor.stripTrailingZeros().scale() > 2)
            throw new IllegalArgumentException("valor deve ter no máximo duas casas decimais");
        valor = valor.setScale(2, RoundingMode.UNNECESSARY);
    }
}
