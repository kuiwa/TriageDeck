package io.github.wangkui.triagedeck.security.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;
import java.util.List;

import io.github.wangkui.triagedeck.platform.security.RequirePermission;
import io.github.wangkui.triagedeck.security.auth.CurrentActor;
import io.github.wangkui.triagedeck.security.auth.PermissionService;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.HandlerMethod;

class PermissionInterceptorTest {

    private PermissionInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new PermissionInterceptor(new PermissionService());
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void should_allow_when_actor_has_required_permission() throws Exception {
        authenticateAs("operator");

        assertThat(interceptor.preHandle(request(), response(), handler("clear"))).isTrue();
    }

    @Test
    void should_deny_when_actor_lacks_required_permission() throws Exception {
        authenticateAs("viewer");

        assertThatThrownBy(() -> interceptor.preHandle(request(), response(), handler("clear")))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void should_deny_when_handler_has_no_permission_annotation() throws Exception {
        authenticateAs("admin");

        assertThatThrownBy(() -> interceptor.preHandle(request(), response(), handler("undeclared")))
                .isInstanceOf(AccessDeniedException.class);
    }

    private static void authenticateAs(String role) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new CurrentActor("test-user", "test-subject", List.of(role)),
                        null,
                        List.of()));
    }

    private static HandlerMethod handler(String methodName) throws NoSuchMethodException {
        Method method = TestController.class.getMethod(methodName);
        return new HandlerMethod(new TestController(), method);
    }

    private static MockHttpServletRequest request() {
        return new MockHttpServletRequest();
    }

    private static MockHttpServletResponse response() {
        return new MockHttpServletResponse();
    }

    public static class TestController {

        @RequirePermission(obj = "alert", act = "clear")
        public void clear() {
        }

        public void undeclared() {
        }
    }
}
