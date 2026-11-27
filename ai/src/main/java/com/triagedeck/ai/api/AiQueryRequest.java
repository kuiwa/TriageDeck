package com.triagedeck.ai.api;

/**
 * AI 自然语言查询请求（openapi {@code AiQueryRequest}）。
 */
public record AiQueryRequest(
        String question, // 必填，1~500 字
        Integer limit    // 1~50，默认 20
) {
}