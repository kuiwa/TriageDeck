package com.triagedeck.ai.api;

import com.triagedeck.ai.AiBriefingService;
import com.triagedeck.alert.api.port.AlertReadPort;
import com.triagedeck.platform.security.RequirePermission;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

/**
 * 告警简报接口（openapi #8 {@code POST /api/ai/briefing/{id}}）。
 * 权限 {@code ai:briefing}；SSE 返回 delta/done 事件。仅读告警，不做写操作。
 *
 * 注意：必须直接返回 {@link SseEmitter}（不能包成 ResponseEntity&lt;Object&gt;），
 * 否则 Spring 不会将其识别为流式返回值，会把 SseEmitter 当普通对象序列化导致 500。
 * 错误通过异常 + {@link AiBriefingExceptionHandler} 输出 JSON。
 */
@RestController
public class AiBriefingController {

    private final AiBriefingService briefingService;
    private final AlertReadPort alertReadPort;
    private final boolean enabled;

    public AiBriefingController(AiBriefingService briefingService,
                                AlertReadPort alertReadPort,
                                @Value("${app.ai.enabled:true}") boolean enabled) {
        this.briefingService = briefingService;
        this.alertReadPort = alertReadPort;
        this.enabled = enabled;
    }

    @PostMapping(value = "/api/ai/briefing/{id}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @RequirePermission(obj = "ai", act = "briefing")
    public SseEmitter briefing(@PathVariable UUID id) {
        if (!enabled) {
            throw new AiBriefingUnavailableException();
        }
        if (alertReadPort.findById(id) == null) {
            throw new AlertNotFoundForBriefingException(id);
        }
        return briefingService.briefingSse(id);
    }

    /** AI 功能关闭 → 503。 */
    public static class AiBriefingUnavailableException extends RuntimeException {
        public AiBriefingUnavailableException() {
            super("AI briefing disabled");
        }
    }

    /** 告警不存在 → 404。 */
    public static class AlertNotFoundForBriefingException extends RuntimeException {
        public AlertNotFoundForBriefingException(UUID id) {
            super("Alert not found: " + id);
        }
    }
}