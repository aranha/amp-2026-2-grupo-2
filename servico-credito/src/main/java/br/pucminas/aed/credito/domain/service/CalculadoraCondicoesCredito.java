package br.pucminas.aed.credito.domain.service;

import br.pucminas.aed.credito.domain.model.CondicoesDeCredito;
import br.pucminas.aed.credito.domain.model.Dinheiro;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Objects;

/** Serviço de domínio puro: calcula prestação fixa pelo sistema Price.
 * Não define política de taxa, prazo, tarifas ou CET: esses valores vêm de fora.
 */
public final class CalculadoraCondicoesCredito {
    private static final MathContext PRECISAO = MathContext.DECIMAL128;

    public CondicoesDeCredito calcular(Dinheiro valorAprovado, BigDecimal taxaMensal, int parcelas) {
        Objects.requireNonNull(valorAprovado, "valor aprovado obrigatorio");
        Objects.requireNonNull(taxaMensal, "taxa mensal obrigatoria");
        if (taxaMensal.signum() < 0) throw new IllegalArgumentException("taxa mensal negativa");
        if (parcelas < 1) throw new IllegalArgumentException("parcelas deve ser positivo");

        BigDecimal principal = valorAprovado.valor();
        BigDecimal prestacao;
        if (taxaMensal.signum() == 0) {
            prestacao = principal.divide(BigDecimal.valueOf(parcelas), 2, RoundingMode.HALF_UP);
        } else {
            BigDecimal fator = BigDecimal.ONE.add(taxaMensal, PRECISAO).pow(parcelas, PRECISAO);
            BigDecimal numerador = principal.multiply(taxaMensal, PRECISAO).multiply(fator, PRECISAO);
            BigDecimal denominador = fator.subtract(BigDecimal.ONE, PRECISAO);
            prestacao = numerador.divide(denominador, 2, RoundingMode.HALF_UP);
        }
        if (prestacao.signum() <= 0) throw new IllegalArgumentException("prestacao arredondada nao pode ser zero");
        BigDecimal total = prestacao.multiply(BigDecimal.valueOf(parcelas));
        return new CondicoesDeCredito(valorAprovado, taxaMensal, parcelas,
                new Dinheiro(prestacao), new Dinheiro(total));
    }
}
