package com.triagedeck.ai.api;

import com.triagedeck.ai.service.AiQueryService;
import com.triagedeck.platform.security.RequirePermission;
import com.triagedeck.platform.trace.TraceIds;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 自然语言查询接口（openapi #9 {@code POST /api/ai/query}）。
 * 权限 {@code ai:query}。仅读告警，不做任何写操作。
 */
@RestController
public class AiQueryController {

    private static final int MAX_QUESTION_LEN = 500;

    private final AiQueryService queryService;
    private final TraceIds traceIds;

    public AiQueryController(AiQueryService queryService, TraceIds traceIds) {
        this.queryService = queryService;
        this.traceIds = traceIds;
    }

    @PostMapping("/api/ai/query")
    @RequirePermission(obj = "ai", act = "query")
    public ResponseEntity<Object> query(@RequestBody AiQueryRequest request) {
        String question = request == null ? null : request.question();
        if (question == null || question.isBlank() || question.length() > MAX_QUESTION_LEN) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new AiApiError("VALIDATION_FAILED", "问题不能为空且不超过 500 字", traceIds.current()));
        }

        try {
            return ResponseEntity.ok(queryService.query(request));
        } catch (AiQueryService.AiQueryParseException e) {
            String hint = e.droppedFields.isEmpty()
                    ? "无法从问题中解析出受控条件，请换一种说法（可指定级别/来源/关键词/时间）"
                    : "包含不允许的字段：" + e.droppedFields;
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(new AiApiError("AI_UNPARSEABLE", hint, traceIds.current()));
        } catch (AiQueryService.AiQueryUnavailableException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new AiApiError("AI_UNAVAILABLE", "AI 服务暂不可用，请稍后重试", traceIds.current()));
        }
    }
}