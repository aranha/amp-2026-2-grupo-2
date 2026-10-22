package br.pucminas.aed.credito.domain.model;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class SolicitacaoDeCreditoTest {
    @Test void impedeAceitarDuasVezes() {
        var solicitacao = new SolicitacaoDeCredito("s-1", new Dinheiro(new BigDecimal("100.00")));
        var quando = OffsetDateTime.parse("2026-10-21T12:00:00-03:00");
        assertEquals("s-1", solicitacao.aceitar(quando).solicitacaoId());
        assertEquals(SolicitacaoDeCredito.Estado.ACEITA, solicitacao.estado());
        assertThrows(PropostaJaAceitaException.class, () -> solicitacao.aceitar(quando));
    }
    @Test void dinheiroRejeitaValorInvalido() {
        assertThrows(IllegalArgumentException.class, () -> new Dinheiro(BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class, () -> new Dinheiro(new BigDecimal("12.345")));
        assertEquals(new BigDecimal("12.30"), new Dinheiro(new BigDecimal("12.3")).valor());
    }
    @Test void cpfValidaDigitos() {
        assertEquals("52998224725", new CPF("529.982.247-25").numero());
        assertThrows(IllegalArgumentException.class, () -> new CPF("111.111.111-11"));
        assertThrows(IllegalArgumentException.class, () -> new CPF("529.982.247-26"));
    }
}
