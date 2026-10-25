package br.pucminas.aed.risco.application;

import static org.assertj.core.api.Assertions.assertThat;

import br.pucminas.aed.risco.domain.Solicitacao;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class FluxoDeSolicitacoesServiceTest {

    @Test
    void agrupaMesmoInstanteRepresentadoComOffsetsDiferentes(CapturedOutput saida) {
        var service = new FluxoDeSolicitacoesService(288);

        service.agregar("evt-001", solicitacao("100.00", "2026-08-22T12:02:00Z"));
        service.agregar("evt-002", solicitacao("200.00", "2026-08-22T09:03:00-03:00"));

        assertThat(saida).contains(
                "janela=2026-08-22T09:00-03:00 | quantidade=2 | totalSolicitado=300.00");
    }

    @Test
    void separaEventosNoLimiteDaJanela(CapturedOutput saida) {
        var service = new FluxoDeSolicitacoesService(288);

        service.agregar("evt-003", solicitacao("100.00", "2026-08-22T12:04:59-03:00"));
        service.agregar("evt-004", solicitacao("200.00", "2026-08-22T12:05:00-03:00"));

        assertThat(saida)
                .contains("janela=2026-08-22T12:00-03:00 | quantidade=1")
                .contains("janela=2026-08-22T12:05-03:00 | quantidade=1");
    }

    @Test
    void atualizaJanelaCorrespondenteQuandoEventoChegaAtrasado(CapturedOutput saida) {
        var service = new FluxoDeSolicitacoesService(288);

        service.agregar("evt-005", solicitacao("500.00", "2026-08-22T12:07:00-03:00"));
        service.agregar("evt-006", solicitacao("100.00", "2026-08-22T12:02:00-03:00"));
        service.agregar("evt-007", solicitacao("200.00", "2026-08-22T12:03:00-03:00"));

        assertThat(saida).contains(
                "janela=2026-08-22T12:00-03:00 | quantidade=2 | totalSolicitado=300.00");
    }

    @Test
    void naoContabilizaNovamenteQuandoMesmoEventoEReentregue(CapturedOutput saida) {
        var service = new FluxoDeSolicitacoesService(288);
        var solicitacao = solicitacao("100.00", "2026-08-22T13:02:00-03:00");

        service.agregar("evt-008", solicitacao);
        service.agregar("evt-008", solicitacao);

        assertThat(saida).contains(
                "janela=2026-08-22T13:00-03:00 | quantidade=1 | totalSolicitado=100.00");
    }

    @Test
    void descartaJanelasMaisAntigasQuandoAtingeLimite(CapturedOutput saida) {
        var service = new FluxoDeSolicitacoesService(2);

        service.agregar("evt-009", solicitacao("100.00", "2026-08-22T14:02:00-03:00"));
        service.agregar("evt-010", solicitacao("100.00", "2026-08-22T14:07:00-03:00"));
        service.agregar("evt-011", solicitacao("100.00", "2026-08-22T14:12:00-03:00"));
        service.agregar("evt-012", solicitacao("100.00", "2026-08-22T14:02:00-03:00"));

        assertThat(saida).doesNotContain(
                "janela=2026-08-22T14:00-03:00 | quantidade=2");
    }

    @Test
    void removeIdsSomenteDasJanelasDescartadas() {
        var service = new FluxoDeSolicitacoesService(2);

        service.agregar("antigo-1", solicitacao("100.00", "2026-08-22T14:02:00-03:00"));
        service.agregar("antigo-2", solicitacao("200.00", "2026-08-22T14:03:00-03:00"));
        service.agregar("retido-1", solicitacao("100.00", "2026-08-22T14:07:00-03:00"));
        service.agregar("retido-2", solicitacao("100.00", "2026-08-22T14:12:00-03:00"));

        assertThat(idsAgregados(service)).containsExactlyInAnyOrder("retido-1", "retido-2");

        // Uma janela atrasada descartada imediatamente tambem deve liberar seu ID.
        service.agregar("atrasado", solicitacao("100.00", "2026-08-22T14:02:00-03:00"));

        assertThat(idsAgregados(service)).containsExactlyInAnyOrder("retido-1", "retido-2");
    }

    @Test
    void preservaDeduplicacaoDasJanelasRetidasAposLimpeza(CapturedOutput saida) {
        var service = new FluxoDeSolicitacoesService(1);
        service.agregar("expirado", solicitacao("100.00", "2026-08-22T14:02:00-03:00"));
        var retido = solicitacao("200.00", "2026-08-22T14:07:00-03:00");
        service.agregar("retido", retido);
        service.agregar("retido", retido);

        assertThat(saida).contains("Evento duplicado ignorado no fluxo de credito solicitado | eventoId=retido")
                .doesNotContain("quantidade=2");
    }

    @SuppressWarnings("unchecked")
    private java.util.Set<String> idsAgregados(FluxoDeSolicitacoesService service) {
        return (java.util.Set<String>) org.springframework.test.util.ReflectionTestUtils
                .getField(service, "eventosAgregados");
    }

    private Solicitacao solicitacao(String valor, String dataSolicitacao) {
        return new Solicitacao(
                UUID.randomUUID(),
                "cli-ficticio-001",
                new BigDecimal(valor),
                OffsetDateTime.parse(dataSolicitacao));
    }
}
