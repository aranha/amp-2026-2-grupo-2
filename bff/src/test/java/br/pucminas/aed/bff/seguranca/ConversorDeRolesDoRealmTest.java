package br.pucminas.aed.bff.seguranca;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ConversorDeRolesDoRealmTest {

    private final ConversorDeRolesDoRealm conversor = new ConversorDeRolesDoRealm();

    @Test
    void traduzRolesDoRealmParaAuthoritiesComPrefixoRole() {
        var authorities = conversor.convert(tokenCom(Map.of("roles", List.of("analista", "cliente"))))
            .getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();

        assertThat(authorities).containsExactlyInAnyOrder("ROLE_analista", "ROLE_cliente");
    }

    @Test
    void usaOPreferredUsernameComoNomeDoPrincipal() {
        assertThat(conversor.convert(tokenCom(Map.of("roles", List.of("analista")))).getName())
            .isEqualTo("ana.analista");
    }

    @Test
    void tokenSemRealmAccessNaoGanhaNenhumaAuthority() {
        Jwt semRealmAccess = Jwt.withTokenValue("t").header("alg", "RS256").claim("sub", "ana").build();

        assertThat(conversor.convert(semRealmAccess).getAuthorities()).isEmpty();
    }

    @Test
    void realmAccessComFormatoInesperadoNaoQuebra() {
        assertThat(conversor.convert(tokenCom(Map.of("roles", "analista"))).getAuthorities()).isEmpty();
    }

    private Jwt tokenCom(Map<String, Object> realmAccess) {
        return Jwt.withTokenValue("t")
            .header("alg", "RS256")
            .claim("preferred_username", "ana.analista")
            .claim("realm_access", realmAccess)
            .build();
    }
}
