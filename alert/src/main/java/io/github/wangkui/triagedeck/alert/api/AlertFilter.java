package io.github.wangkui.triagedeck.alert.api;

/**
 * Filter criteria for querying alerts (read-only).
 * Used by AlertReadPort.findAll() and AI briefing query filtering.
 * All fields are optional - omit to match all.
 * 
 * 白名单过滤：只允许以下字段，其它被拒绝
 */
public record AlertFilter(
        /** 严重程度过滤: CRITICAL, HIGH, MEDIUM, LOW, INFO */
        Severity severity,

        /** 源头过滤: 如 "payment-api", "auth-service" */
        String source,

        /** 关键字在 message/resourceId/eventCode 中模糊匹配 */
        String keyword,

        /** 从时间 (ISO-8601 UTC), inclusive */
        Instant occurredAtFrom,

        /** 到时间 (ISO-8601 UTC), inclusive */
        Instant occurredAtTo
) {
    // Whitelist: only these fields are allowed in filter
    // sql, orderBy, page, size 等被拒绝（由服务端控制）

    /**
     * Validate that only whitelisted fields are used.
     * @param fieldName the field name to check
     * @return true if allowed, false if rejected
     */
    public static boolean isWhitelistedField(String fieldName) {
        return fieldName != null && (
                "severity".equals(fieldName) ||
                "source".equals(fieldName) ||
                "keyword".equals(fieldName) ||
                "occurredAtFrom".equals(fieldName) ||
                "occurredAtTo".equals(fieldName)
        );
    }
}