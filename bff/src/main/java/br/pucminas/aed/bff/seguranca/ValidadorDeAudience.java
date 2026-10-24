package br.pucminas.aed.bff.seguranca;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

public class ValidadorDeAudience implements OAuth2TokenValidator<Jwt> {

    private final String audienceEsperada;

    public ValidadorDeAudience(String audienceEsperada) {
        this.audienceEsperada = audienceEsperada;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        if (token.getAudience() != null && token.getAudience().contains(audienceEsperada)) {
            return OAuth2TokenValidatorResult.success();
        }
        return OAuth2TokenValidatorResult.failure(new OAuth2Error(
            "invalid_token",
            "Token sem a audience esperada: " + audienceEsperada,
            "https://tools.ietf.org/html/rfc6750#section-3.1"));
    }
}
