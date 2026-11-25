package io.github.wangkui.triagedeck.audit;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AuditCommand(
        UUID id,
        Instant occurredAt,
        String actor,
        ActorType actorType,
        AuditAction action,
        TargetType targetType,
        String targetId,
        Outcome outcome,
        String source,
        String sourceEventId,
        String clientNet,
        String traceId,
        Map<String, Object> detail) {
}