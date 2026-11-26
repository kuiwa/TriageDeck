package com.triagedeck.audit;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.triagedeck.platform.audit.AccessDeniedAuditPort;
import com.triagedeck.platform.audit.ActorType;
import com.triagedeck.platform.audit.AuditAction;
import com.triagedeck.platform.audit.Outcome;
import com.triagedeck.platform.audit.TargetType;
import com.triagedeck.platform.security.SecurityContext;
import com.triagedeck.platform.trace.TraceIds;
import org.springframework.stereotype.Component;

@Component
public class AccessDeniedAuditRecorder implements AccessDeniedAuditPort {

    private final AuditWriter auditWriter;
    private final SecurityContext securityContext;
    private final TraceIds traceIds;

    public AccessDeniedAuditRecorder(AuditWriter auditWriter, SecurityContext securityContext, TraceIds traceIds) {
        this.auditWriter = auditWriter;
        this.securityContext = securityContext;
        this.traceIds = traceIds;
    }

    @Override
    public void recordDenied(String actor, String obj, String act, String path, String method, String clientNet, String traceId) {
        String effectiveActor = actor != null ? actor : securityContext.getCurrentUser();
        if (effectiveActor == null) {
            effectiveActor = "SYSTEM";
        }

        auditWriter.write(new AuditCommand(
                UUID.randomUUID(),
                Instant.now(),
                effectiveActor,
                ActorType.USER,
                AuditAction.ACCESS_DENIED,
                TargetType.ENDPOINT,
                obj + ":" + act,
                Outcome.DENIED,
                "APP",
                null,
                clientNet,
                traceId,
                Map.of(
                    "requestedObj", obj,
                    "requestedAct", act,
                    "path", path,
                    "method", method,
                    "detail", "Access denied for " + obj + ":" + act)
        ));
    }
}