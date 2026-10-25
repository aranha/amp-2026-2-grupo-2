package br.pucminas.aed.risco.adapter.in.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.pucminas.aed.risco.application.port.in.ConsultarAnalises;
import br.pucminas.aed.risco.domain.AnaliseDeCredito;
import br.pucminas.aed.risco.domain.Solicitacao;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

/** O adaptador HTTP sozinho: a porta de entrada e um mock. */
@WebMvcTest(AnalisesController.class)
class AnalisesControllerTest {

    private static final UUID ID = UUID.fromString("3f1c9a60-7b4e-4a21-9c0d-5e2a1b8f4d33");

    @Autowired private MockMvc http;
    @MockBean private ConsultarAnalises consulta;

    @Test
    void parecerSaiComOsCamposDoEventoEOHorarioDeBrasilia() throws Exception {
        when(consulta.buscar(ID)).thenReturn(Optional.of(analise("150000.00")));

        http.perform(get("/v1/analises/{id}", ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.solicitacaoId").value(ID.toString()))
                .andExpect(jsonPath("$.elegivel").value(true))
                .andExpect(jsonPath("$.valorAprovado").value(100000.00))
                .andExpect(jsonPath("$.valorSolicitado").value(150000.00))
                .andExpect(jsonPath("$.motivo").value("Aprovado parcialmente: valor limitado a R$ 100.000,00"))
                .andExpect(jsonPath("$.dataAnalise").value("2026-10-25T10:00:00.123-03:00"))
                .andExpect(jsonPath("$.situacaoDaReserva").value("PENDENTE"))
                .andExpect(jsonPath("$.clienteId").doesNotExist());
    }

    @Test
    void solicitacaoSemAnaliseDevolve404() throws Exception {
        when(consulta.buscar(any())).thenReturn(Optional.empty());

        http.perform(get("/v1/analises/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void idForaDoFormatoTambemE404ParaNaoAbrirOCircuitoDoBff() throws Exception {
        http.perform(get("/v1/analises/{id}", "nao-e-um-uuid")).andExpect(status().isNotFound());

        verify(consulta, never()).buscar(any());
    }

    @Test
    void listaVemNumEnvelope() throws Exception {
        when(consulta.listarRecentes(50)).thenReturn(List.of(analise("18500.00"), analise("500.00")));

        http.perform(get("/v1/analises"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.analises.length()").value(2))
                .andExpect(jsonPath("$.analises[1].elegivel").value(false))
                .andExpect(jsonPath("$.analises[1].valorAprovado").value(0.00));
    }

    @Test
    void limiteRecusadoPelaConsultaDevolve400() throws Exception {
        when(consulta.listarRecentes(anyInt())).thenThrow(new IllegalArgumentException("limite deve ficar entre 1 e 200"));

        http.perform(get("/v1/analises").param("limite", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("limite deve ficar entre 1 e 200"));
    }

    private static AnaliseDeCredito analise(String valor) {
        return AnaliseDeCredito.avaliar(
                new Solicitacao(ID, "cli-ficticio-001", new BigDecimal(valor), OffsetDateTime.parse("2026-10-25T09:59:00-03:00")),
                OffsetDateTime.parse("2026-10-25T10:00:00.123-03:00"));
    }
}
