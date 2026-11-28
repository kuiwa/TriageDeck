package com.triagedeck.ai.api;

import com.triagedeck.alert.api.AlertView;
import java.time.Instant;

/**
 * AI 查询单条结果（openapi {@code AiQueryItem}）：AlertView 字段 + 清除字段（未清则为 null）。
 */
public record AiQueryItem(
        java.util.UUID id,
        String severity,
        String source,
        String type,
        String resourceId,
        String eventCode,
        String message,
        String firstSeenAt,
        String lastSeenAt,
        long occurrenceCount,
        String fingerprint,
        Instant clearedAt,
        String clearedBy,
        String clearedReason,
        String clearMode
) {
    public static AiQueryItem from(AlertView alert) {
        return new AiQueryItem(
                alert.id(), alert.severity().name(), alert.source(), alert.type(),
                alert.resourceId(), alert.eventCode(), alert.message(),
                alert.firstSeenAt().toString(), alert.lastSeenAt().toString(),
                alert.occurrenceCount(), alert.fingerprint(),
                null, null, null, null
        );
    }
}