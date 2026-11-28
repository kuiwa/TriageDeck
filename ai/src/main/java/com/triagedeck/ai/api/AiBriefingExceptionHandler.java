package com.triagedeck.ai.api;

import com.triagedeck.platform.trace.TraceIds;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 仅处理 AiBriefingController 的错误：404 告警不存在、503 AI 关闭。
 * 必须显式 contentType=application/json，因为该端点 produces text/event-stream。
 */
@RestControllerAdvice(assignableTypes = AiBriefingController.class)
public class AiBriefingExceptionHandler {

    private final TraceIds traceIds;

    public AiBriefingExceptionHandler(TraceIds traceIds) {
        this.traceIds = traceIds;
    }

    @ExceptionHandler(AiBriefingController.AlertNotFoundForBriefingException.class)
    public ResponseEntity<AiApiError> notFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new AiApiError("ALERT_NOT_FOUND", "告警不存在", traceIds.current()));
    }

    @ExceptionHandler(AiBriefingController.AiBriefingUnavailableException.class)
    public ResponseEntity<AiApiError> unavailable() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new AiApiError("AI_UNAVAILABLE", "AI 服务暂不可用", traceIds.current()));
    }
}