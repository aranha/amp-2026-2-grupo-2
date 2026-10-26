package br.pucminas.aed.risco.adapter.out.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import br.pucminas.aed.risco.domain.AnaliseDeCredito;
import br.pucminas.aed.risco.domain.SituacaoDaReserva;
import br.pucminas.aed.risco.domain.Solicitacao;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

/** O SQL do adaptador contra o schema.sql de verdade, em H2 no modo PostgreSQL. */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JdbcAnalisesDeCredito.class)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:risco-jdbc;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver"})
class JdbcAnalisesDeCreditoTest {

    @Autowired private JdbcAnalisesDeCredito analises;

    @Test
    void umaAnalisePorSolicitacao() {
        var solicitacao = solicitacao("18500.00");

        assertThat(analises.registrar(AnaliseDeCredito.avaliar(solicitacao, agora(0)))).isTrue();
        assertThat(analises.registrar(AnaliseDeCredito.avaliar(solicitacao, agora(1)))).isFalse();
    }

    @Test
    void reconstituiOQueGravouNoHorarioDeBrasilia() {
        var gravada = AnaliseDeCredito.avaliar(solicitacao("150000.00"), agora(0));
        analises.registrar(gravada);

        var lida = analises.buscar(gravada.solicitacaoId()).orElseThrow();

        assertThat(lida.solicitacao()).isEqualTo(gravada.solicitacao());
        assertThat(lida.parecer()).isEqualTo(gravada.parecer());
        assertThat(lida.analisadaEm()).isEqualTo(gravada.analisadaEm());
        assertThat(lida.situacaoDaReserva()).isEqualTo(SituacaoDaReserva.PENDENTE);
    }

    @Test
    void transicaoSoGravaSeASituacaoAnteriorConfere() {
        var analise = AnaliseDeCredito.avaliar(solicitacao("18500.00"), agora(0));
        analises.registrar(analise);
        UUID id = analise.solicitacaoId();

        assertThat(analises.atualizarSituacao(id, SituacaoDaReserva.PENDENTE, SituacaoDaReserva.RESERVADA)).isTrue();
        assertThat(analises.atualizarSituacao(id, SituacaoDaReserva.PENDENTE, SituacaoDaReserva.CANCELADA)).isFalse();
        assertThat(analises.buscar(id).orElseThrow().situacaoDaReserva()).isEqualTo(SituacaoDaReserva.RESERVADA);
    }

    @Test
    void listaAsMaisRecentesPrimeiro() {
        var antiga = AnaliseDeCredito.avaliar(solicitacao("1000.00"), agora(0));
        var nova = AnaliseDeCredito.avaliar(solicitacao("2000.00"), agora(10));
        var meio = AnaliseDeCredito.avaliar(solicitacao("3000.00"), agora(5));
        analises.registrar(antiga);
        analises.registrar(nova);
        analises.registrar(meio);

        assertThat(analises.listarRecentes(2)).extracting(AnaliseDeCredito::solicitacaoId)
                .containsExactly(nova.solicitacaoId(), meio.solicitacaoId());
    }

    @Test
    void solicitacaoDesconhecidaNaoEncontra() {
        assertThat(analises.buscar(UUID.randomUUID())).isEmpty();
    }

    private static OffsetDateTime agora(int minutos) {
        return OffsetDateTime.parse("2026-10-25T10:00:00.123-03:00").plusMinutes(minutos);
    }

    private static Solicitacao solicitacao(String valor) {
        return new Solicitacao(UUID.randomUUID(), "cli-ficticio-001", new BigDecimal(valor),
                OffsetDateTime.parse("2026-10-25T09:59:00-03:00"));
    }
}
