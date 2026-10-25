package br.pucminas.aed.risco.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.pucminas.aed.risco.domain.AnaliseDeCredito;
import br.pucminas.aed.risco.domain.Solicitacao;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ConsultaDeAnalisesServiceTest {

    private final PortasEmMemoria.Analises analises = new PortasEmMemoria.Analises();
    private final ConsultaDeAnalisesService consulta = new ConsultaDeAnalisesService(analises);

    @Test
    void listaAsMaisRecentesPrimeiroAteOLimite() {
        var antiga = registrar("2026-10-25T09:00:00-03:00");
        var recente = registrar("2026-10-25T11:00:00-03:00");
        registrar("2026-10-25T08:00:00-03:00");

        assertThat(consulta.listarRecentes(2)).extracting(AnaliseDeCredito::solicitacaoId)
                .containsExactly(recente, antiga);
    }

    @Test
    void limiteForaDaFaixaEPedidoInvalido() {
        assertThatThrownBy(() -> consulta.listarRecentes(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> consulta.listarRecentes(201)).isInstanceOf(IllegalArgumentException.class);
    }

    private UUID registrar(String analisadaEm) {
        var quando = OffsetDateTime.parse(analisadaEm);
        var id = UUID.randomUUID();
        analises.registrar(AnaliseDeCredito.avaliar(
                new Solicitacao(id, "cli-ficticio-001", new BigDecimal("5000.00"), quando), quando));
        return id;
    }
}
