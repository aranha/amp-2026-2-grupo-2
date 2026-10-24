package br.pucminas.aed.bff.solicitacao;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SolicitacaoControllerTest {

    private static final String SOLICITACAO = "3f1c9a60-7b4e-4a21-9c0d-5e2a1b8f4d33";
    private static final String SUB = "9c1e5a77-1f2b-4a83-9a0e-77c3b2d41f55";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SolicitacaoClient credito;

    @Test
    void aceitaEApontaParaAConsultaDaProposta() throws Exception {
        when(credito.criar(any(), any(), any(), any())).thenReturn(recebida());

        mockMvc.perform(post("/v1/solicitacoes")
                .with(jwt().jwt(j -> j.subject(SUB)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"valorSolicitado\": 18500.00}"))
            .andExpect(status().isAccepted())
            .andExpect(header().string("Location", "/v2/propostas/" + SOLICITACAO))
            .andExpect(jsonPath("$.solicitacaoId").value(SOLICITACAO));
    }

    @Test
    void oClienteVemDoTokenNaoDoCorpo() throws Exception {
        when(credito.criar(any(), any(), any(), any())).thenReturn(recebida());

        mockMvc.perform(post("/v1/solicitacoes")
                .with(jwt().jwt(j -> j.subject(SUB)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"valorSolicitado\": 18500.00, \"clienteId\": \"outra-pessoa\"}"))
            .andExpect(status().isAccepted());

        verify(credito).criar(eq(SUB), eq(new BigDecimal("18500.00")), eq("app-cliente"), any());
    }

    @Test
    void valorNaoPositivoNemChegaNoCredito() throws Exception {
        mockMvc.perform(post("/v1/solicitacoes")
                .with(jwt().jwt(j -> j.subject(SUB)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"valorSolicitado\": 0}"))
            .andExpect(status().isBadRequest());

        verify(credito, never()).criar(any(), any(), any(), any());
    }

    @Test
    void semValorNemChegaNoCredito() throws Exception {
        mockMvc.perform(post("/v1/solicitacoes")
                .with(jwt().jwt(j -> j.subject(SUB)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest());

        verify(credito, never()).criar(any(), any(), any(), any());
    }

    @Test
    void semTokenNaoSolicita() throws Exception {
        mockMvc.perform(post("/v1/solicitacoes")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"valorSolicitado\": 18500.00}"))
            .andExpect(status().isUnauthorized());

        verify(credito, never()).criar(any(), any(), any(), any());
    }

    private SolicitacaoRecebida recebida() {
        return new SolicitacaoRecebida("e-1", SOLICITACAO, SUB, new BigDecimal("18500.00"),
            OffsetDateTime.parse("2026-10-24T13:00:00Z"), "app-cliente");
    }
}
