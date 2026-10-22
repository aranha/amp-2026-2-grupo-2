package br.pucminas.aed.credito.domain.service;

import br.pucminas.aed.credito.domain.model.Dinheiro;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CalculadoraCondicoesCreditoTest {
    private final CalculadoraCondicoesCredito calculadora = new CalculadoraCondicoesCredito();
    private static Dinheiro reais(String valor) { return new Dinheiro(new BigDecimal(valor)); }

    @Test void taxaZeroDivideOPrincipal() {
        var resultado = calculadora.calcular(reais("1200"), BigDecimal.ZERO, 12);
        assertEquals(new BigDecimal("100.00"), resultado.valorParcela().valor());
        assertEquals(new BigDecimal("1200.00"), resultado.valorTotal().valor());
    }

    @Test void taxaPositivaGeraPrestacaoPrice() {
        var resultado = calculadora.calcular(reais("1000"), new BigDecimal("0.02"), 12);
        assertEquals(new BigDecimal("94.56"), resultado.valorParcela().valor());
        assertEquals(new BigDecimal("1134.72"), resultado.valorTotal().valor());
    }

    @Test void rejeitaTaxaNegativaEParcelasInvalidas() {
        assertThrows(IllegalArgumentException.class,
                () -> calculadora.calcular(reais("1000"), new BigDecimal("-0.01"), 12));
        assertThrows(IllegalArgumentException.class,
                () -> calculadora.calcular(reais("1000"), BigDecimal.ZERO, 0));
    }

    @Test void rejeitaEntradasNulas() {
        assertThrows(NullPointerException.class,
                () -> calculadora.calcular(null, BigDecimal.ZERO, 12));
        assertThrows(NullPointerException.class,
                () -> calculadora.calcular(reais("1000"), null, 12));
    }
}
