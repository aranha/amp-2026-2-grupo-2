package br.pucminas.aed.bff.analise;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/analises")
public class AnalisesController {

    private final AnalisesClient risco;

    public AnalisesController(AnalisesClient risco) {
        this.risco = risco;
    }

    @GetMapping
    public RespostaDeAnalises listar(@AuthenticationPrincipal Jwt token) {
        return new RespostaDeAnalises(token.getClaimAsString("preferred_username"), risco.listar());
    }
}
