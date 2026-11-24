package io.github.wangkui.triagedeck.security.api;

import java.util.Set;

import io.github.wangkui.triagedeck.security.auth.CurrentActor;
import io.github.wangkui.triagedeck.security.auth.PermissionService;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@Profile("!prod")
@ConditionalOnProperty(name = "app.security.test-endpoints", havingValue = "true")
public class PermissionCheckController {

    private final PermissionService permissionService;

    public PermissionCheckController(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    @GetMapping("/api/_perm/check")
    public PermCheckView check(
            @RequestParam String obj,
            @RequestParam String act,
            @AuthenticationPrincipal CurrentActor actor) {
        if (!permissionService.isValidObjectAndAction(obj, act)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Unknown permission object or action");
        }
        Set<String> roles = Set.copyOf(actor.roles());
        return new PermCheckView(
                permissionService.isAllowed(roles, obj, act),
                actor.roles(),
                obj,
                act);
    }

    public record PermCheckView(
            boolean allowed,
            java.util.List<String> roles,
            String obj,
            String act) {
    }
}
