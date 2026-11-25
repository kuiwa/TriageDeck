package io.github.wangkui.triagedeck.security.config;

import io.github.wangkui.triagedeck.security.auth.PermissionService;

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
        registry.addInterceptor(permissionInterceptor);
    }
}
