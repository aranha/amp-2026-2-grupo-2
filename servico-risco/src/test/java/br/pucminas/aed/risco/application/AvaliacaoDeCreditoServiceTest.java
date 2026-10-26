package br.pucminas.aed.risco.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.pucminas.aed.risco.domain.Solicitacao;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** O caso de uso com as portas em memoria: sem Spring, sem Kafka, sem banco. */
class AvaliacaoDeCreditoServiceTest {

    private static final Clock RELOGIO = Clock.fixed(Instant.parse("2026-10-25T13:00:00.123456Z"), ZoneOffset.of("-03:00"));

    private final PortasEmMemoria.Eventos eventos = new PortasEmMemoria.Eventos();
    private final PortasEmMemoria.Analises analises = new PortasEmMemoria.Analises();
    private final PortasEmMemoria.Publicador publicador = new PortasEmMemoria.Publicador();
    private final AvaliacaoDeCreditoService casoDeUso =
            new AvaliacaoDeCreditoService(eventos, analises, publicador, RELOGIO);

    @Test
    void avaliaGravaEPublicaOParecer() {
        var solicitacao = solicitacao("18500.00");

        var analise = casoDeUso.avaliar("evt-1", solicitacao);

        assertThat(analise).isPresent();
        assertThat(analises.gravadas).containsKey(solicitacao.id());
        assertThat(publicador.publicados).singleElement().satisfies(evento -> {
            assertThat(evento.solicitacaoId()).isEqualTo(solicitacao.id());
            assertThat(evento.elegivel()).isTrue();
            assertThat(evento.valorAprovado()).isEqualByComparingTo("18500.00");
            // Horario de Brasilia, truncado no milissegundo: o mesmo valor vai para o banco e para o evento.
            assertThat(evento.dataAnalise()).isEqualTo(OffsetDateTime.parse("2026-10-25T10:00:00.123-03:00"));
        });
    }

    @Test
    void parecerRecusadoTambemEPublicado() {
        casoDeUso.avaliar("evt-1", solicitacao("300000.00"));

        assertThat(publicador.publicados).singleElement().satisfies(evento -> {
            assertThat(evento.elegivel()).isFalse();
            assertThat(evento.valorAprovado()).isEqualByComparingTo("0.00");
        });
    }

    @Test
    void mesmoEventoEntregueDeNovoNaoGravaNemPublica() {
        var solicitacao = solicitacao("18500.00");
        casoDeUso.avaliar("evt-1", solicitacao);

        assertThat(casoDeUso.avaliar("evt-1", solicitacao)).isEmpty();

        assertThat(publicador.publicados).hasSize(1);
    }

    @Test
    void outroEventoDaMesmaSolicitacaoNaoGeraSegundoParecer() {
        var solicitacao = solicitacao("18500.00");
        casoDeUso.avaliar("evt-no-topico-novo", solicitacao);

        assertThat(casoDeUso.avaliar("evt-no-topico-herdado", solicitacao)).isEmpty();

        assertThat(analises.gravadas).hasSize(1);
        assertThat(publicador.publicados).hasSize(1);
        assertThat(eventos.processados).containsExactlyInAnyOrder("evt-no-topico-novo", "evt-no-topico-herdado");
    }

    @Test
    void falhaNaPublicacaoSobeParaQuemChamou() {
        publicador.falha = new IllegalStateException("broker fora do ar");

        assertThatThrownBy(() -> casoDeUso.avaliar("evt-1", solicitacao("18500.00")))
                .hasMessage("broker fora do ar");
    }

    private static Solicitacao solicitacao(String valor) {
        return new Solicitacao(UUID.randomUUID(), "cli-ficticio-001", new BigDecimal(valor),
                OffsetDateTime.parse("2026-10-25T09:59:00-03:00"));
    }
}
