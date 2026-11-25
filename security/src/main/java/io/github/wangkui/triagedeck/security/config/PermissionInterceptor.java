package io.github.wangkui.triagedeck.security.config;

import java.util.Set;

import io.github.wangkui.triagedeck.platform.security.RequirePermission;
import io.github.wangkui.triagedeck.security.auth.CurrentActor;
import io.github.wangkui.triagedeck.security.auth.PermissionService;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public final class PermissionInterceptor implements HandlerInterceptor {

    private final PermissionService permissionService;

    public PermissionInterceptor(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        RequirePermission permission = handlerMethod.getMethodAnnotation(RequirePermission.class);
        if (permission == null) {
            throw new AccessDeniedException("Controller handler has no permission declaration");
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof CurrentActor actor)) {
            throw new AccessDeniedException("Authenticated actor is required");
        }

        if (!permissionService.isAllowed(Set.copyOf(actor.roles()), permission.obj(), permission.act())) {
            throw new AccessDeniedException("Permission denied");
        }
        return true;
    }
}
