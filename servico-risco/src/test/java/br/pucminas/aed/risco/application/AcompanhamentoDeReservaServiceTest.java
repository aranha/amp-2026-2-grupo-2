package br.pucminas.aed.risco.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.pucminas.aed.risco.domain.AnaliseDeCredito;
import br.pucminas.aed.risco.domain.SituacaoDaReserva;
import br.pucminas.aed.risco.domain.Solicitacao;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AcompanhamentoDeReservaServiceTest {

    private final PortasEmMemoria.Eventos eventos = new PortasEmMemoria.Eventos();
    private final PortasEmMemoria.Analises analises = new PortasEmMemoria.Analises();
    private final AcompanhamentoDeReservaService casoDeUso = new AcompanhamentoDeReservaService(eventos, analises);
    private final UUID solicitacaoId = UUID.randomUUID();

    @BeforeEach
    void analiseJaRegistrada() {
        var agora = OffsetDateTime.parse("2026-10-25T10:00:00-03:00");
        analises.registrar(AnaliseDeCredito.avaliar(
                new Solicitacao(solicitacaoId, "cli-ficticio-001", new BigDecimal("3000.00"), agora), agora));
    }

    @Test
    void reservaECancelamentoChegamAAnalise() {
        assertThat(casoDeUso.registrarReserva("evt-res", solicitacaoId)).isTrue();
        assertThat(situacao()).isEqualTo(SituacaoDaReserva.RESERVADA);

        assertThat(casoDeUso.registrarCancelamento("evt-can", solicitacaoId)).isTrue();
        assertThat(situacao()).isEqualTo(SituacaoDaReserva.CANCELADA);
    }

    @Test
    void reservaAtrasadaNaoDesfazOCancelamento() {
        casoDeUso.registrarCancelamento("evt-can", solicitacaoId);

        assertThat(casoDeUso.registrarReserva("evt-res", solicitacaoId)).isFalse();

        assertThat(situacao()).isEqualTo(SituacaoDaReserva.CANCELADA);
        assertThat(eventos.processados).containsExactlyInAnyOrder("evt-can", "evt-res");
    }

    @Test
    void mesmoEventoDaSagaAplicaUmaVezSo() {
        casoDeUso.registrarReserva("evt-res", solicitacaoId);

        assertThat(casoDeUso.registrarReserva("evt-res", solicitacaoId)).isFalse();
    }

    @Test
    void eventoQueChegaAntesDaAnaliseEFalhaTransitoria() {
        var desconhecida = UUID.randomUUID();

        assertThatThrownBy(() -> casoDeUso.registrarReserva("evt-res", desconhecida))
                .isInstanceOf(AnaliseAindaNaoRegistradaException.class)
                .isNotInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void transicaoConcorrenteEFalhaTransitoria() {
        analises.simularConflito = true;

        assertThatThrownBy(() -> casoDeUso.registrarReserva("evt-res", solicitacaoId))
                .isInstanceOf(ConflitoDeAtualizacaoException.class);
    }

    private SituacaoDaReserva situacao() {
        return analises.buscar(solicitacaoId).orElseThrow().situacaoDaReserva();
    }
}
