package br.pucminas.aed.bff.analise;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/v1/analises")
public class AnalisesController {

    private static final List<AnaliseResumo> PROVISORIAS = List.of(
        new AnaliseResumo("3f1c9a60-7b4e-4a21-9c0d-5e2a1b8f4d33", true,
            new BigDecimal("18500.00"), "Renda compatível e sem restrição", Instant.parse("2026-10-24T13:02:11Z")),
        new AnaliseResumo("8b2d4e17-0a95-4c73-b6f1-2d9c7e5a1044", false,
            new BigDecimal("0.00"), "Comprometimento de renda acima do limite", Instant.parse("2026-10-24T13:40:52Z")));

    @GetMapping
    public RespostaDeAnalises listar(@AuthenticationPrincipal Jwt token) {
        return new RespostaDeAnalises(token.getClaimAsString("preferred_username"), PROVISORIAS);
    }
}
