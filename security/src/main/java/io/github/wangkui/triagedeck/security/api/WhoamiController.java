package io.github.wangkui.triagedeck.security.api;

import java.util.List;
import java.util.Set;

import io.github.wangkui.triagedeck.platform.security.RequirePermission;
import io.github.wangkui.triagedeck.security.auth.CurrentActor;
import io.github.wangkui.triagedeck.security.auth.PermissionService;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class WhoamiController {

    private final PermissionService permissionService;

    public WhoamiController(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    @GetMapping("/api/whoami")
    @RequirePermission(obj = "whoami", act = "read")
    public WhoamiView whoami(@AuthenticationPrincipal CurrentActor actor) {
        Set<String> roleSet = Set.copyOf(actor.roles());
        List<PermissionService.Permission> permissions =
                permissionService.permissions(roleSet);
        return new WhoamiView(
                actor.username(),
                actor.subject(),
                actor.roles(),
                permissions);
    }

    public record WhoamiView(
            String username,
            String subject,
            List<String> roles,
            List<PermissionService.Permission> permissions) {
    }
}
