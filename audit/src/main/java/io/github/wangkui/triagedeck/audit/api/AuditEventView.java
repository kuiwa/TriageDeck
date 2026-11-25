package io.github.wangkui.triagedeck.audit.api;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AuditEventView(
        UUID id,
        Instant occurredAt,
        String actor,
        String actorType,
        String action,
        String targetType,
        String targetId,
        String outcome,
        String source,
        String clientNet,
        String traceId,
        Map<String, Object> detail
) {
}