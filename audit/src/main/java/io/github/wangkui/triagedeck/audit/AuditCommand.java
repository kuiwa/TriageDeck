package io.github.wangkui.triagedeck.audit;

import io.github.wangkui.triagedeck.platform.audit.ActorType;
import io.github.wangkui.triagedeck.platform.audit.AuditAction;
import io.github.wangkui.triagedeck.platform.audit.Outcome;
import io.github.wangkui.triagedeck.platform.audit.TargetType;

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