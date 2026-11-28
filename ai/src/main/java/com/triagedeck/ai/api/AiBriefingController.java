package com.triagedeck.ai.api;

import com.triagedeck.ai.AiBriefingService;
import com.triagedeck.alert.api.port.AlertReadPort;
import com.triagedeck.platform.security.RequirePermission;
import com.triagedeck.platform.trace.TraceIds;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

/**
 * 告警简报接口（openapi #8 {@code POST /api/ai/briefing/{id}}）。
 * 权限 {@code ai:briefing}；SSE 返回 delta/done 事件。仅读告警，不做写操作。
 */
@RestController
public class AiBriefingController {

    private final AiBriefingService briefingService;
    private final AlertReadPort alertReadPort;
    private final TraceIds traceIds;
    private final boolean enabled;

    public AiBriefingController(AiBriefingService briefingService,
                                AlertReadPort alertReadPort,
                                TraceIds traceIds,
                                @Value("${app.ai.enabled:true}") boolean enabled) {
        this.briefingService = briefingService;
        this.alertReadPort = alertReadPort;
        this.traceIds = traceIds;
        this.enabled = enabled;
    }

    @PostMapping(value = "/api/ai/briefing/{id}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @RequirePermission(obj = "ai", act = "briefing")
    public ResponseEntity<Object> briefing(@PathVariable UUID id) {
        if (!enabled) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new AiApiError("AI_UNAVAILABLE", "AI 服务暂不可用", traceIds.current()));
        }
        if (alertReadPort.findById(id) == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new AiApiError("ALERT_NOT_FOUND", "告警不存在", traceIds.current()));
        }
        SseEmitter emitter = briefingService.briefingSse(id);
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(emitter);
    }
}