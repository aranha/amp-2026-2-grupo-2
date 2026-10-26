package br.pucminas.aed.risco.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/** O resultado da regra de elegibilidade. Recusado sempre aprova zero. */
public record Parecer(boolean elegivel, BigDecimal valorAprovado, String motivo) {

    public Parecer {
        Objects.requireNonNull(valorAprovado, "valorAprovado e obrigatorio");
        Objects.requireNonNull(motivo, "motivo e obrigatorio");
        if (elegivel && valorAprovado.signum() <= 0) {
            throw new IllegalArgumentException("parecer elegivel aprova valor positivo");
        }
        if (!elegivel && valorAprovado.signum() != 0) {
            throw new IllegalArgumentException("parecer recusado nao aprova valor");
        }
        valorAprovado = valorAprovado.setScale(2, RoundingMode.UNNECESSARY);
    }

    static Parecer aprovado(BigDecimal valor, String motivo) {
        return new Parecer(true, valor, motivo);
    }

    static Parecer recusado(String motivo) {
        return new Parecer(false, BigDecimal.ZERO, motivo);
    }
}
