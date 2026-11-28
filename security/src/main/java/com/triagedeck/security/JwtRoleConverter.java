package com.triagedeck.security.auth;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

public final class JwtRoleConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private static final Set<String> MANAGED_ROLES =
            Set.of("admin", "operator", "viewer", "service");

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Set<String> roles = managedRoles(jwt);
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        roles.forEach(role -> authorities.add(new SimpleGrantedAuthority("ROLE_" + role)));
        return new UsernamePasswordAuthenticationToken(
                currentActor(jwt, roles),
                jwt,
                authorities);
    }

    public static Set<String> managedRoles(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaim("realm_access");
        if (realmAccess == null) {
            return Collections.emptySet();
        }
        Object rawRoles = realmAccess.get("roles");
        if (!(rawRoles instanceof List<?> roles)) {
            return Collections.emptySet();
        }
        Set<String> managed = new LinkedHashSet<>();
        for (Object role : roles) {
            if (role instanceof String value && MANAGED_ROLES.contains(value)) {
                managed.add(value);
            }
        }
        return managed;
    }

    private static CurrentActor currentActor(Jwt jwt, Set<String> roles) {
        String username = jwt.getClaimAsString("preferred_username");
        return new CurrentActor(
                username == null ? "" : username,
                jwt.getSubject(),
                roles.stream().sorted().toList());
    }
}
