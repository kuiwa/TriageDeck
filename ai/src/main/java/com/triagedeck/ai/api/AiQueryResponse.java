package com.triagedeck.ai.api;

import java.util.List;

/**
 * AI 自然语言查询响应（openapi {@code AiQueryResponse}）。
 */
public record AiQueryResponse(
        AiFilter filter,
        List<AiQueryItem> items,
        long total,
        int page,
        int size,
        String explanation,
        List<String> droppedFields
) {
}