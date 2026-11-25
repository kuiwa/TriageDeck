package io.github.wangkui.triagedeck.audit;

import io.github.wangkui.triagedeck.platform.audit.AuditAction;
import io.github.wangkui.triagedeck.platform.audit.AuditTargetResolver;
import io.github.wangkui.triagedeck.platform.audit.TargetType;
import io.github.wangkui.triagedeck.platform.trace.TraceIds;
import io.github.wangkui.triagedeck.platform.security.SecurityContext;
import io.github.wangkui.triagedeck.audit.AuditCommand;
import io.github.wangkui.triagedeck.audit.AuditWriter;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class AuditWriterImpl implements AuditWriter {

    @PersistenceContext
    private EntityManager entityManager;

    private final AuditTargetResolver targetResolver;
    private final TraceIds traceIds;
    private final SecurityContext securityContext;

    public AuditWriterImpl(AuditTargetResolver targetResolver, TraceIds traceIds, SecurityContext securityContext) {
        this.targetResolver = targetResolver;
        this.traceIds = traceIds;
        this.securityContext = securityContext;
    }

    @Transactional(propagation = jakarta.transaction.Propagation.REQUIRES_NEW)
    @Override
    public void write(AuditCommand command) {
        // Resolve target snapshot if needed
        if (command.getTargetType() != null && command.getTargetId() != null) {
            Map<String, Object> snapshot = targetResolver.resolve(command.getTargetType(), command.getTargetId(), command.getTraceId());
            if (snapshot != null) {
                // Merge snapshot fields into command detail
                @SuppressWarnings("unchecked")
                Map<String, Object> detail = (Map<String, Object>) command.getDetail();
                snapshot.forEach((k, v) -> {
                    if (!detail.containsKey(k)) {
                        detail.put(k, v);
                    }
                });
            }
        }

        // Set default values
        if (command.getOccurredAt() == null) {
            command.setOccurredAt(java.time.Instant.now());
        }
        if (command.getSource() == null) {
            command.setSource(securityContext.getCurrentUser() != null ? 
                securityContext.getCurrentUser() : "SYSTEM");
        }
        if (command.getActorType() == null) {
            command.setActorType(ActorType.SERVICE);
        }

        // Write to database
        entityManager.createNativeQuery(
            "INSERT INTO audit_event (id, occurred_at, recorded_at, actor, actor_type, action, " +
            "target_type, target_id, outcome, source, source_event_id, client_net, trace_id, detail) " +
            "VALUES (:id, :occurredAt, now(), :actor, :actorType, :action, " +
            ":targetType, :targetId, :outcome, :source, :sourceEventId, :clientNet, :traceId, :detail::jsonb)")
            .setParameter("id", command.getId())
            .setParameter("occurredAt", command.getOccurredAt())
            .setParameter("actor", command.getActor())
            .setParameter("actorType", command.getActorType())
            .setParameter("action", command.getAction())
            .setParameter("targetType", command.getTargetType())
            .setParameter("targetId", command.getTargetId())
            .setParameter("outcome", command.getOutcome())
            .setParameter("source", command.getSource())
            .setParameter("sourceEventId", command.getSourceEventId())
            .setParameter("clientNet", command.getClientNet())
            .setParameter("traceId", command.getTraceId())
            .setParameter("detail", command.getDetail())
            .executeUpdate();
    }
}