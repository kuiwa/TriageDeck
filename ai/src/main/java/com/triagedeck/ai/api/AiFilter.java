package com.triagedeck.ai.api;

import java.util.List;

/**
 * AI 解析出的受控过滤条件（openapi {@code AiFilter}）。
 * 只允许白名单键：scope/from/to/severities/sources/keyword/clearedBy。
 */
public record AiFilter(
        String scope,
        String from,
        String to,
        List<String> severities,
        List<String> sources,
        String keyword,
        String clearedBy
) {
}