package io.github.wangkui.triagedeck.security.config;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.wangkui.triagedeck.platform.audit.AccessDeniedAuditPort;
import io.github.wangkui.triagedeck.platform.trace.TraceIds;
import io.github.wangkui.triagedeck.security.auth.JwtRoleConverter;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public JwtRoleConverter jwtRoleConverter() {
        return new JwtRoleConverter();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtRoleConverter jwtRoleConverter,
            ObjectMapper objectMapper,
            AccessDeniedAuditPort accessDeniedAuditPort,
            TraceIds traceIds) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health").permitAll()
                        // 前端静态资源与 SPA 入口（docs/02-design.md §3：静态资源不鉴权）
                        .requestMatchers("/", "/index.html", "/assets/**", "/favicon.ico", "/callback")
                        .permitAll()
                        .requestMatchers("/api/whoami", "/api/_perm/check").authenticated()
                        .requestMatchers("/api/alerts/**").authenticated()
                        .requestMatchers("/api/audit/**").authenticated()
                        .requestMatchers("/api/ai/**").authenticated()
                        .requestMatchers("/error").permitAll()
                        // 未匹配的路径交给 MVC 返回契约 NOT_FOUND；未标注 @RequirePermission 的 handler 仍由拦截器默认拒绝
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtRoleConverter))
                        .authenticationEntryPoint(new JsonAuthenticationEntryPoint(objectMapper))
                        .accessDeniedHandler(
                                new JsonAccessDeniedHandler(objectMapper, accessDeniedAuditPort, traceIds)));
        return http.build();
    }
}
