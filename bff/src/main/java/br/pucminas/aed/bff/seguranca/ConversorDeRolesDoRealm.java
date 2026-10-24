package br.pucminas.aed.bff.seguranca;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class ConversorDeRolesDoRealm implements Converter<Jwt, AbstractAuthenticationToken> {

    private static final String CLAIM_REALM_ACCESS = "realm_access";
    private static final String CLAIM_ROLES = "roles";

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        return new JwtAuthenticationToken(jwt, authorities(jwt), nome(jwt));
    }

    private Collection<GrantedAuthority> authorities(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaimAsMap(CLAIM_REALM_ACCESS);
        if (realmAccess == null || !(realmAccess.get(CLAIM_ROLES) instanceof Collection<?> roles)) {
            return Set.of();
        }
        return roles.stream()
            .map(String::valueOf)
            .map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role))
            .collect(Collectors.toUnmodifiableSet());
    }

    private String nome(Jwt jwt) {
        String usuario = jwt.getClaimAsString("preferred_username");
        return usuario != null ? usuario : jwt.getSubject();
    }
}
