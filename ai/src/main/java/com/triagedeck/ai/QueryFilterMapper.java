package com.triagedeck.ai.mapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.triagedeck.alert.api.AlertFilter;
import com.triagedeck.alert.domain.Severity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Maps AI model JSON to AlertFilter with whitelist validation.
 * - 只允许契约 §3 #9 的查询键：scope/from/to/severities/sources/keyword/clearedBy
 * - 白名单外键 → droppedFields + 422 AI_UNPARSEABLE
 * - 危险键（sql/orderBy/limit/offset/page/sort）→ 立即 422
 * - AlertFilter 只能表达单值 severity/source/keyword/from/to，其余键记录到 droppedFields
 */
public final class QueryFilterMapper {

    private static final Set<String> ALLOWED =
            Set.of("scope", "from", "to", "severities", "sources", "keyword", "clearedBy");
    private static final Set<String> DISALLOWED =
            Set.of("sql", "orderBy", "limit", "offset", "page", "sort");
    private static final ObjectMapper JSON = new ObjectMapper();

    private QueryFilterMapper() {
    }

    public static Result<AlertFilter> map(String json) {
        if (json == null || json.isBlank()) {
            return error(List.of());
        }

        Map<String, Object> map;
        try {
            map = JSON.readValue(json, new TypeReference<Map<String, Object>>() { });
        } catch (Exception e) {
            return error(List.of());
        }

        List<String> dropped = new ArrayList<>();

        // 危险键优先判定，避免任何透传
        for (String key : map.keySet()) {
            String lower = key.toLowerCase(Locale.ROOT).trim();
            if (DISALLOWED.contains(lower)) {
                return error(List.of(key));
            }
            if (!ALLOWED.contains(lower)) {
                dropped.add(key);
            }
        }

        Severity severity = parseSeverity(firstValue(map.get("severities")));
        String source = trimmed(firstValue(map.get("sources")));
        String keyword = trimmed(firstValue(map.get("keyword")));
        Instant from = parseInstant(map.get("from"));
        Instant to = parseInstant(map.get("to"));

        // scope=active 是当前读取端口的默认范围，视为已满足，不记为丢弃；
        // 仅当请求 history 等暂不支持的范围时才提示
        if (map.containsKey("scope")) {
            String scope = trimmed(firstValue(map.get("scope")));
            if (scope != null && !scope.equalsIgnoreCase("active")) {
                dropped.add("scope");
            }
        }
        if (map.containsKey("clearedBy")) {
            // clearedBy 仅对 history 有意义，当前读取端口不支持
            dropped.add("clearedBy");
        }

        boolean hasUsable = severity != null || source != null || keyword != null
                || from != null || to != null;
        if (!hasUsable) {
            return error(dropped);
        }

        return new Result<>(new AlertFilter(severity, source, keyword, from, to), dropped, false);
    }

    private static Result<AlertFilter> error(List<String> dropped) {
        return new Result<>(null, dropped, true);
    }

    private static String firstValue(Object value) {
        if (value instanceof String s) {
            return s;
        }
        if (value instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof String s) {
            return s;
        }
        return null;
    }

    private static String trimmed(String value) {
        if (value == null) {
            return null;
        }
        String result = value.trim();
        return result.isEmpty() ? null : result;
    }

    private static Severity parseSeverity(String value) {
        String result = trimmed(value);
        if (result == null) {
            return null;
        }
        try {
            return Severity.valueOf(result.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static Instant parseInstant(Object value) {
        String result = trimmed(firstValue(value));
        if (result == null) {
            return null;
        }
        try {
            return Instant.parse(result);
        } catch (Exception e) {
            // 兼容模型返回带时区偏移的时间（如 +08:00）
            try {
                return java.time.OffsetDateTime.parse(result).toInstant();
            } catch (Exception ignored) {
                return null;
            }
        }
    }

    public static class Result<T> {
        public final T value;
        public final List<String> droppedFields;
        public final boolean isParseError;

        public Result(T value, List<String> droppedFields, boolean isParseError) {
            this.value = value;
            this.droppedFields = droppedFields;
            this.isParseError = isParseError;
        }
    }
}