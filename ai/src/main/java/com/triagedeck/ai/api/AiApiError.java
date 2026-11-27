package com.triagedeck.ai.api;

/**
 * AI 接口统一错误响应（契约 §0.3：code/message/traceId）。
 */
public record AiApiError(String code, String message, String traceId) {
}