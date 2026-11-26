package com.triagedeck.security.config;

import com.triagedeck.security.auth.PermissionService;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class PermissionWebConfig implements WebMvcConfigurer {

    private final PermissionInterceptor permissionInterceptor;

    public PermissionWebConfig(PermissionService permissionService) {
        this.permissionInterceptor = new PermissionInterceptor(permissionService);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // SPA 静态资源与 OIDC 回调落地页没有权限语义，豁免“未标注注解即 403”的默认拒绝；
        // 其余路径仍由 SecurityFilterChain 的 anyRequest().denyAll() 兜底。
        registry.addInterceptor(permissionInterceptor)
                .excludePathPatterns("/", "/index.html", "/assets/**", "/favicon.ico", "/callback", "/error");
    }
}
