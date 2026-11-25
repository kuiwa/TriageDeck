package io.github.wangkui.triagedeck.security.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.Test;

class PermissionServiceTest {

    private final PermissionService permissionService = new PermissionService();

    @Test
    void should_allow_when_any_role_allows() {
        assertThat(permissionService.isAllowed(
                Set.of("viewer", "operator"), "alert", "clear")).isTrue();
    }

    @Test
    void should_deny_alert_clear_for_viewer() {
        assertThat(permissionService.isAllowed(
                Set.of("viewer"), "alert", "clear")).isFalse();
    }

    @Test
    void should_not_match_wildcard() {
        assertThat(permissionService.isAllowed(
                Set.of("operator"), "alert", "*")).isFalse();
    }
}
