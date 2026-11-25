package io.github.wangkui.triagedeck.audit;

import io.github.wangkui.triagedeck.platform.audit.AccessDeniedAuditPort;
import io.github.wangkui.triagedeck.platform.audit.AuditAction;
import io.github.wangkui.triagedeck.platform.audit.TargetType;
import io.github.wangkui.triagedeck.platform.security.SecurityContext;
import io.github.wangkui.triagedeck.platform.trace.TraceIds;
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
                AuditAction.valueOf(act.toUpperCase()),
                TargetType.ENDPOINT,
                act,  // targetId will be set by caller or use act as placeholder
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