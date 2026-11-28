package com.triagedeck.audit;

import com.triagedeck.platform.audit.ActorType;
import com.triagedeck.platform.audit.AuditAction;
import com.triagedeck.platform.audit.Outcome;
import com.triagedeck.platform.audit.TargetType;

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