package br.pucminas.aed.bff.seguranca;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ValidadorDeAudienceTest {

    private final ValidadorDeAudience validador = new ValidadorDeAudience("bff-api");

    @Test
    void aceitaTokenComAAudienceEsperada() {
        assertThat(validador.validate(tokenComAudience("bff-api")).hasErrors()).isFalse();
    }

    @Test
    void aceitaTokenComVariasAudiencesDesdeQueAEsperadaEstejaLa() {
        assertThat(validador.validate(tokenComAudience("account", "bff-api")).hasErrors()).isFalse();
    }

    @Test
    void recusaTokenDeOutroClient() {
        assertThat(validador.validate(tokenComAudience("core-bancario")).hasErrors()).isTrue();
    }

    @Test
    void recusaTokenSemAudience() {
        Jwt semAud = Jwt.withTokenValue("t").header("alg", "RS256").claim("sub", "ana").build();

        assertThat(validador.validate(semAud).hasErrors()).isTrue();
    }

    private Jwt tokenComAudience(String... audiences) {
        return Jwt.withTokenValue("t")
            .header("alg", "RS256")
            .audience(List.of(audiences))
            .build();
    }
}
