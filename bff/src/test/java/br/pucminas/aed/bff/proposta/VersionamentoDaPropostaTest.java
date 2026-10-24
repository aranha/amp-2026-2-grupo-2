package br.pucminas.aed.bff.proposta;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class VersionamentoDaPropostaTest {

    private static final String ID = "3f1c9a60-7b4e-4a21-9c0d-5e2a1b8f4d33";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AgregadorDePropostas agregador;

    @BeforeEach
    void propostaAgregada() {
        when(agregador.agregar(anyString())).thenReturn(new PropostaV2(
            ID, "p-1", true, "ATIVO",
            new CondicoesV2(new BigDecimal("18500.00"), new BigDecimal("0.019"), 24, new BigDecimal("0.0241")),
            new ParecerV2(true, "Renda compativel", OffsetDateTime.parse("2026-10-24T13:01:00Z")),
            new AntifraudeV2(true, 870, "Sem indicio", OffsetDateTime.parse("2026-10-24T13:02:00Z")),
            false, false, OffsetDateTime.parse("2026-10-24T13:02:11Z")));
    }

    @Test
    void v2AninhaAsCondicoesENaoTemTaxaNoTopo() throws Exception {
        mockMvc.perform(get("/v2/propostas/{id}", ID).with(jwt()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.condicoes.taxa").value(0.019))
            .andExpect(jsonPath("$.condicoes.cet").value(0.0241))
            .andExpect(jsonPath("$.taxa").doesNotExist())
            .andExpect(jsonPath("$.parecer.motivo").value("Renda compativel"))
            .andExpect(jsonPath("$.antifraude.score").value(870));
    }

    @Test
    void v1MantemOsCamposPlanosENaoTemCondicoes() throws Exception {
        mockMvc.perform(get("/v1/propostas/{id}", ID).with(jwt()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.taxa").value(0.019))
            .andExpect(jsonPath("$.valorAprovado").value(18500.00))
            .andExpect(jsonPath("$.parcelas").value(24))
            .andExpect(jsonPath("$.condicoes").doesNotExist());
    }

    @Test
    void v1AvisaQueVaiSairDoAr() throws Exception {
        mockMvc.perform(get("/v1/propostas/{id}", ID).with(jwt()))
            .andExpect(header().string("Deprecation", "Wed, 22 Oct 2026 00:00:00 GMT"))
            .andExpect(header().string("Sunset", "Wed, 31 Dec 2026 00:00:00 GMT"))
            .andExpect(header().string("Link", "</v2/propostas/" + ID + ">; rel=\"successor-version\""));
    }

    @Test
    void v2NaoAvisaNada() throws Exception {
        mockMvc.perform(get("/v2/propostas/{id}", ID).with(jwt()))
            .andExpect(header().doesNotExist("Deprecation"))
            .andExpect(header().doesNotExist("Sunset"));
    }

    @Test
    void asDuasVersoesExigemToken() throws Exception {
        mockMvc.perform(get("/v1/propostas/{id}", ID)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/v2/propostas/{id}", ID)).andExpect(status().isUnauthorized());
    }

    @Test
    void propostaInexistenteDevolve404() throws Exception {
        when(agregador.agregar(anyString())).thenThrow(new PropostaNaoEncontradaException(ID));

        mockMvc.perform(get("/v2/propostas/{id}", ID).with(jwt()))
            .andExpect(status().isNotFound());
    }

    @Test
    void creditoForaDoArDevolve503EDizQualFonte() throws Exception {
        when(agregador.agregar(anyString()))
            .thenThrow(new FonteIndisponivelException("credito", new IllegalStateException("conexao recusada")));

        mockMvc.perform(get("/v2/propostas/{id}", ID).with(jwt()))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.fonte").value("credito"));
    }
}
