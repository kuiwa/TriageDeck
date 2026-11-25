package io.github.wangkui.triagedeck.security.config;

import java.io.IOException;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.wangkui.triagedeck.platform.audit.AccessDeniedAuditPort;
import io.github.wangkui.triagedeck.security.api.ApiError;
import io.github.wangkui.triagedeck.platform.trace.TraceIds;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

public final class JsonAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;
    private final AccessDeniedAuditPort auditPort;
    private final TraceIds traceIds;

    @Autowired
    public JsonAccessDeniedHandler(ObjectMapper objectMapper,
                                   AccessDeniedAuditPort auditPort,
                                   TraceIds traceIds) {
        this.objectMapper = objectMapper;
        this.auditPort = auditPort;
        this.traceIds = traceIds;
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException exception) throws IOException {
        String traceId = traceIds.current();
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(),
                new ApiError("ACCESS_DENIED", "无权访问该资源", traceId));

        // 403 越权审计：写在 handler 里，不要用 @RestControllerAdvice
        try {
            String actor = request.getRemoteUser();
            if (actor == null) actor = "unknown";
            auditPort.recordDenied(
                    actor,
                    "alert",
                    "clear",
                    request.getRequestURI(),
                    request.getMethod(),
                    request.getRemoteAddr(),
                    traceId);
        } catch (Exception e) {
            // 审计失败不影响 403 响应
        }
    }
}
