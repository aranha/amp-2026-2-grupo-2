package br.pucminas.aed.bff.analise;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Com o cliente de verdade e o Resilience4j ligado: o risco inalcançável passa por retry,
 * circuit breaker e fallback, e o analista recebe 503 dizendo a fonte, não um 500.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "bff.downstreams.risco=http://127.0.0.1:1")
class AnalisesViaRiscoTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void riscoForaDoArDevolve503ComAFonte() throws Exception {
        mockMvc.perform(get("/v1/analises").with(jwt()
                .jwt(token -> token.claim("preferred_username", "ana.analista"))
                .authorities(new SimpleGrantedAuthority("ROLE_analista"))))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.fonte").value("risco"));
    }

    @Test
    void semARoleContinuaSendo403AntesDeChamarORisco() throws Exception {
        mockMvc.perform(get("/v1/analises").with(jwt()
                .authorities(new SimpleGrantedAuthority("ROLE_cliente"))))
            .andExpect(status().isForbidden());
    }
}
