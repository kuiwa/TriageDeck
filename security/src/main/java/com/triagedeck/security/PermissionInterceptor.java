package com.triagedeck.security.config;

import java.util.Set;

import com.triagedeck.platform.security.RequirePermission;
import com.triagedeck.security.auth.CurrentActor;
import com.triagedeck.security.auth.PermissionService;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PermissionInterceptor implements HandlerInterceptor {

    /** 403 审计用：由拦截器写入，AccessDeniedHandler 读取 */
    public static final String DENIED_OBJ_ATTRIBUTE = "triagedeck.denied.obj";
    public static final String DENIED_ACT_ATTRIBUTE = "triagedeck.denied.act";

    private static final Logger log = LoggerFactory.getLogger(PermissionInterceptor.class);

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
        request.setAttribute(DENIED_OBJ_ATTRIBUTE,
                permission != null ? permission.obj() : "unknown");
        request.setAttribute(DENIED_ACT_ATTRIBUTE,
                permission != null ? permission.act() : "unknown");
        if (permission == null) {
            log.warn("action=permission_denied reason=missing_annotation handler={} path={}",
                    handlerMethod.getShortLogMessage(), request.getRequestURI());
            throw new AccessDeniedException("Controller handler has no permission declaration");
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof CurrentActor actor)) {
            log.warn("action=permission_denied reason=no_actor obj={} act={} path={}",
                    permission.obj(), permission.act(), request.getRequestURI());
            throw new AccessDeniedException("Authenticated actor is required");
        }

        if (!permissionService.isAllowed(Set.copyOf(actor.roles()), permission.obj(), permission.act())) {
            log.warn("action=permission_denied reason=casbin roles={} obj={} act={} path={}",
                    actor.roles(), permission.obj(), permission.act(), request.getRequestURI());
            throw new AccessDeniedException("Permission denied");
        }
        return true;
    }
}
