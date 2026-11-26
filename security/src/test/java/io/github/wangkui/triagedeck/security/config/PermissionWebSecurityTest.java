package io.github.wangkui.triagedeck.security.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.wangkui.triagedeck.platform.audit.AccessDeniedAuditPort;
import io.github.wangkui.triagedeck.platform.security.RequirePermission;
import io.github.wangkui.triagedeck.platform.trace.TraceIds;
import io.github.wangkui.triagedeck.security.api.PermissionCheckController;
import io.github.wangkui.triagedeck.security.api.WhoamiController;
import io.github.wangkui.triagedeck.security.auth.PermissionService;

import jakarta.servlet.Filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = PermissionWebSecurityTest.TestConfiguration.class)
@TestPropertySource(properties = "app.security.test-endpoints=true")
class PermissionWebSecurityTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private Filter springSecurityFilterChain;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(springSecurityFilterChain)
                .build();
    }

    @Test
    void should_return_whoami_when_operator_has_permission() throws Exception {
        mockMvc.perform(get("/api/whoami")
                        .header("Authorization", "Bearer operator"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("operator"))
                .andExpect(jsonPath("$.roles[0]").value("operator"));
    }

    @Test
    void should_return_401_when_whoami_has_no_bearer_token() throws Exception {
        mockMvc.perform(get("/api/whoami"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.traceId").exists());
    }

    @Test
    void should_not_return_401_for_spa_entry_points_without_bearer_token() throws Exception {
        // 测试切片没有装配静态资源，放行后应落到 404 而不是被安全链拦成 401
        mockMvc.perform(get("/"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/index.html"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/assets/index.js"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/callback"))
                .andExpect(status().isNotFound());
    }

    @Test
    void should_return_403_when_viewer_calls_admin_only_permission_check() throws Exception {
        mockMvc.perform(get("/api/_perm/check")
                        .param("obj", "alert")
                        .param("act", "clear")
                        .header("Authorization", "Bearer viewer"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.traceId").exists());
    }

    @Test
    void should_allow_admin_to_use_permission_check_endpoint() throws Exception {
        mockMvc.perform(get("/api/_perm/check")
                        .param("obj", "alert")
                        .param("act", "clear")
                        .header("Authorization", "Bearer admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(true));
    }

    @Test
    void should_return_403_for_unannotated_handler() throws Exception {
        mockMvc.perform(get("/api/test/undeclared")
                        .header("Authorization", "Bearer admin"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void should_allow_service_to_reach_annotated_alert_ingest_handler() throws Exception {
        mockMvc.perform(post("/api/alerts/test-ingest")
                        .header("Authorization", "Bearer service"))
                .andExpect(status().isOk());
    }

    @Test
    void should_deny_viewer_at_annotated_alert_ingest_handler() throws Exception {
        mockMvc.perform(post("/api/alerts/test-ingest")
                        .header("Authorization", "Bearer viewer"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @Import({
        SecurityConfig.class,
        PermissionWebConfig.class,
        PermissionService.class,
        WhoamiController.class,
        PermissionCheckController.class,
        TestController.class,
        AlertTestController.class
    })
    static class TestConfiguration {

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        AccessDeniedAuditPort accessDeniedAuditPort() {
            return (actor, obj, act, path, method, clientNet, traceId) -> {
                // 测试切片只验证 403 响应 envelope，不校验审计落库
            };
        }

        @Bean
        TraceIds traceIds() {
            return new TraceIds();
        }

        @Bean
        JwtDecoder jwtDecoder() {
            return token -> {
                List<String> roles = switch (token) {
                    case "admin" -> List.of("admin");
                    case "operator" -> List.of("operator");
                    case "viewer" -> List.of("viewer");
                    case "service" -> List.of("service");
                    default -> List.of();
                };
                Instant now = Instant.now();
                return Jwt.withTokenValue(token)
                        .header("alg", "none")
                        .subject("subject-" + token)
                        .issuedAt(now)
                        .expiresAt(now.plusSeconds(300))
                        .claim("preferred_username", token)
                        .claim("realm_access", Map.of("roles", roles))
                        .build();
            };
        }
    }

    @RestController
    static class TestController {

        @GetMapping("/api/test/clear")
        @RequirePermission(obj = "alert", act = "clear")
        String clear() {
            return "cleared";
        }

        @GetMapping("/api/test/undeclared")
        String undeclared() {
            return "must be denied";
        }
    }

    @RestController
    static class AlertTestController {

        @PostMapping("/api/alerts/test-ingest")
        @RequirePermission(obj = "alert", act = "ingest")
        String ingest() {
            return "accepted";
        }
    }
}
