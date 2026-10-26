package br.pucminas.aed.risco.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Sem Spring, sem Kafka, sem banco: o dominio roda sozinho. E a prova da regra de dependencia. */
class AnaliseDeCreditoTest {

    private static final OffsetDateTime AGORA = OffsetDateTime.parse("2026-10-25T10:00:00-03:00");

    @ParameterizedTest(name = "R$ {0} -> elegivel={1}, aprovado R$ {2}")
    @CsvSource({
            "999.99,     false, 0.00",
            "1000.00,    true,  1000.00",
            "18500.00,   true,  18500.00",
            "100000.00,  true,  100000.00",
            "100000.01,  true,  100000.00",
            "200000.00,  true,  100000.00",
            "200000.01,  false, 0.00"})
    void regraDeElegibilidadeNasFronteiras(String solicitado, boolean elegivel, String aprovado) {
        var analise = AnaliseDeCredito.avaliar(solicitacao(solicitado), AGORA);

        assertThat(analise.parecer().elegivel()).isEqualTo(elegivel);
        assertThat(analise.parecer().valorAprovado()).isEqualByComparingTo(aprovado);
    }

    @Test
    void dentroDoLimiteAprovaOValorPedido() {
        var analise = AnaliseDeCredito.avaliar(solicitacao("18500.00"), AGORA);

        assertThat(analise.parecer()).isEqualTo(
                new Parecer(true, new BigDecimal("18500.00"), "Valor dentro do limite de concessão automática"));
        assertThat(analise.analisadaEm()).isEqualTo(AGORA);
    }

    @Test
    void acimaDoLimiteAprovaSoOLimiteEDizPorque() {
        var analise = AnaliseDeCredito.avaliar(solicitacao("150000.00"), AGORA);

        assertThat(analise.parecer().valorAprovado()).isEqualByComparingTo(AnaliseDeCredito.LIMITE_AUTOMATICO);
        assertThat(analise.parecer().motivo()).startsWith("Aprovado parcialmente");
        assertThat(analise.solicitacao().valorSolicitado()).isEqualByComparingTo("150000.00");
    }

    @Test
    void recusadoNaoAprovaNada() {
        var analise = AnaliseDeCredito.avaliar(solicitacao("500.00"), AGORA);

        assertThat(analise.parecer().elegivel()).isFalse();
        assertThat(analise.parecer().valorAprovado()).isEqualByComparingTo("0.00");
        assertThat(analise.parecer().motivo()).contains("mínimo");
    }

    @Test
    void nasceComAReservaPendente() {
        assertThat(AnaliseDeCredito.avaliar(solicitacao("18500.00"), AGORA).situacaoDaReserva())
                .isEqualTo(SituacaoDaReserva.PENDENTE);
    }

    @Test
    void reservaECancelamentoSoAvancam() {
        var analise = AnaliseDeCredito.avaliar(solicitacao("18500.00"), AGORA);

        assertThat(analise.registrarReserva()).isTrue();
        assertThat(analise.registrarReserva()).isFalse();
        assertThat(analise.registrarCancelamento()).isTrue();
        assertThat(analise.registrarCancelamento()).isFalse();
        assertThat(analise.situacaoDaReserva()).isEqualTo(SituacaoDaReserva.CANCELADA);
    }

    @Test
    void reservaAtrasadaNaoDesfazOCancelamento() {
        var analise = AnaliseDeCredito.avaliar(solicitacao("18500.00"), AGORA);
        analise.registrarCancelamento();

        assertThat(analise.registrarReserva()).isFalse();
        assertThat(analise.situacaoDaReserva()).isEqualTo(SituacaoDaReserva.CANCELADA);
    }

    @Test
    void oEventoEspelhaOParecer() {
        var analise = AnaliseDeCredito.avaliar(solicitacao("150000.00"), AGORA);

        var evento = analise.elegibilidadeAvaliada();

        assertThat(evento.solicitacaoId()).isEqualTo(analise.solicitacaoId());
        assertThat(evento.elegivel()).isTrue();
        assertThat(evento.valorAprovado()).isEqualByComparingTo("100000.00");
        assertThat(evento.motivo()).isEqualTo(analise.parecer().motivo());
        assertThat(evento.dataAnalise()).isEqualTo(AGORA);
    }

    @Test
    void reavaliarAMesmaSolicitacaoGeraOMesmoEventoId() {
        var solicitacao = solicitacao("18500.00");

        var primeira = AnaliseDeCredito.avaliar(solicitacao, AGORA).elegibilidadeAvaliada();
        var reprocessada = AnaliseDeCredito.avaliar(solicitacao, AGORA.plusMinutes(5)).elegibilidadeAvaliada();
        var outra = AnaliseDeCredito.avaliar(solicitacao("18500.00"), AGORA).elegibilidadeAvaliada();

        assertThat(reprocessada.eventoId()).isEqualTo(primeira.eventoId());
        assertThat(outra.eventoId()).isNotEqualTo(primeira.eventoId());
    }

    @Test
    void reconstituirNaoReaplicaARegra() {
        var parecerGravado = new Parecer(false, BigDecimal.ZERO, "Regra antiga");

        var analise = AnaliseDeCredito.reconstituir(solicitacao("18500.00"), parecerGravado, AGORA,
                SituacaoDaReserva.RESERVADA);

        assertThat(analise.parecer()).isEqualTo(parecerGravado);
        assertThat(analise.situacaoDaReserva()).isEqualTo(SituacaoDaReserva.RESERVADA);
    }

    @Test
    void solicitacaoNormalizaOValorEmDuasCasas() {
        assertThat(solicitacao("15000").valorSolicitado()).isEqualTo(new BigDecimal("15000.00"));
    }

    @Test
    void solicitacaoInvalidaERecusadaComoEntradaInvalida() {
        UUID id = UUID.randomUUID();
        assertThatThrownBy(() -> new Solicitacao(id, "cli", BigDecimal.ZERO, AGORA))
                .isInstanceOf(SolicitacaoInvalidaException.class)
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Solicitacao(id, "cli", new BigDecimal("10.001"), AGORA))
                .isInstanceOf(SolicitacaoInvalidaException.class);
        assertThatThrownBy(() -> new Solicitacao(id, " ", BigDecimal.TEN, AGORA))
                .isInstanceOf(SolicitacaoInvalidaException.class);
        assertThatThrownBy(() -> new Solicitacao(null, "cli", BigDecimal.TEN, AGORA))
                .isInstanceOf(SolicitacaoInvalidaException.class);
        assertThatThrownBy(() -> new Solicitacao(id, "cli", BigDecimal.TEN, null))
                .isInstanceOf(SolicitacaoInvalidaException.class);
    }

    @Test
    void parecerIncoerenteNaoExiste() {
        assertThatThrownBy(() -> new Parecer(true, BigDecimal.ZERO, "x")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Parecer(false, BigDecimal.TEN, "x")).isInstanceOf(IllegalArgumentException.class);
    }

    private static Solicitacao solicitacao(String valor) {
        return new Solicitacao(UUID.randomUUID(), "cli-ficticio-001", new BigDecimal(valor), AGORA.minusMinutes(1));
    }
}
