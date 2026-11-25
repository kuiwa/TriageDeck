package io.github.wangkui.triagedeck.security.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class JwtRoleConverterTest {

    @Test
    void should_keep_only_managed_roles() {
        Jwt jwt = jwt(Map.of("realm_access", Map.of(
                "roles", List.of("operator", "offline_access", "viewer"))));

        assertThat(JwtRoleConverter.managedRoles(jwt))
                .containsExactly("operator", "viewer");
    }

    @Test
    void should_return_empty_when_no_managed_role() {
        Jwt jwt = jwt(Map.of("realm_access", Map.of(
                "roles", List.of("offline_access"))));

        assertThat(JwtRoleConverter.managedRoles(jwt)).isEmpty();
    }

    private Jwt jwt(Map<String, Object> claims) {
        return Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .subject("subject-1")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .claims(existing -> existing.putAll(claims))
                .build();
    }
}
