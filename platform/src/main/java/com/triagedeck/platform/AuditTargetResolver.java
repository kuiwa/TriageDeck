package com.triagedeck.platform.audit;

import com.triagedeck.platform.audit.TargetType;

import java.util.Map;

public interface AuditTargetResolver {
    /**
     * Resolve audit target snapshot for the given targetType and targetId.
     * Returns null if the target is not found or not supported.
     */
    Map<String, Object> resolve(String targetType, String targetId, String traceId);
}