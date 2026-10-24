package br.pucminas.aed.bff.seguranca;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.Map;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SegurancaDaApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void semTokenDevolve401() throws Exception {
        mockMvc.perform(get("/v1/analises"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void comRoleClienteDevolve403() throws Exception {
        mockMvc.perform(get("/v1/analises").with(token("maria.cliente", "cliente")))
            .andExpect(status().isForbidden());
    }

    @Test
    void comRoleAnalistaDevolve200EOUsuarioDoToken() throws Exception {
        mockMvc.perform(get("/v1/analises").with(token("ana.analista", "analista")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.consultadoPor").value("ana.analista"))
            .andExpect(jsonPath("$.analises[0].solicitacaoId").exists());
    }

    @Test
    void healthContinuaPublico() throws Exception {
        mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk());
    }

    @Test
    void preflightDaSpaPassaSemToken() throws Exception {
        mockMvc.perform(options("/v1/analises")
                .header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "GET"))
            .andExpect(status().isOk())
            .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }

    @Test
    void origemDesconhecidaNaoGanhaCors() throws Exception {
        mockMvc.perform(options("/v1/analises")
                .header("Origin", "http://malicioso.example")
                .header("Access-Control-Request-Method", "GET"))
            .andExpect(status().isForbidden());
    }

    private RequestPostProcessor token(String usuario, String... roles) {
        Jwt jwt = Jwt.withTokenValue("token-de-teste")
            .header("alg", "RS256")
            .claim("preferred_username", usuario)
            .claim("realm_access", Map.of("roles", List.of(roles)))
            .build();
        return authentication(new ConversorDeRolesDoRealm().convert(jwt));
    }
}
