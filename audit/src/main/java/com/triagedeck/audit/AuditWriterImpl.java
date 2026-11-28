package com.triagedeck.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.triagedeck.platform.audit.ActorType;
import com.triagedeck.platform.audit.AuditTargetResolver;
import com.triagedeck.platform.trace.TraceIds;
import com.triagedeck.platform.security.SecurityContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Component
public class AuditWriterImpl implements AuditWriter {

    private static final Logger log = LoggerFactory.getLogger(AuditWriterImpl.class);

    @PersistenceContext
    private EntityManager entityManager;

    private final ObjectProvider<AuditTargetResolver> targetResolverProvider;
    private final TraceIds traceIds;
    private final SecurityContext securityContext;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate requiresNew;

    public AuditWriterImpl(ObjectProvider<AuditTargetResolver> targetResolverProvider,
                           TraceIds traceIds,
                           SecurityContext securityContext,
                           ObjectMapper objectMapper,
                           PlatformTransactionManager transactionManager) {
        this.targetResolverProvider = targetResolverProvider;
        this.traceIds = traceIds;
        this.securityContext = securityContext;
        this.objectMapper = objectMapper;
        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        log.info("action=audit_writer_initialized txManager={}",
                transactionManager.getClass().getSimpleName());
    }

    @Override
    public void write(AuditCommand command) {
        // 显式 REQUIRES_NEW 事务，不依赖 @Transactional 代理是否生效
        requiresNew.executeWithoutResult(status -> insert(command));
    }

    private void insert(AuditCommand command) {
        Map<String, Object> detail = new HashMap<>();
        if (command.detail() != null) {
            detail.putAll(command.detail());
        }

        String targetType = command.targetType() != null ? command.targetType().name() : null;
        AuditTargetResolver targetResolver = targetResolverProvider.getIfAvailable();
        if (targetResolver != null && targetType != null && command.targetId() != null) {
            Map<String, Object> snapshot =
                    targetResolver.resolve(targetType, command.targetId(), command.traceId());
            if (snapshot != null) {
                snapshot.forEach(detail::putIfAbsent);
            }
        }

        Instant occurredAt = command.occurredAt() != null ? command.occurredAt() : Instant.now();
        String currentUser = securityContext.getCurrentUser();
        String source = command.source() != null
                ? command.source()
                : (currentUser != null ? currentUser : "SYSTEM");
        ActorType actorType = command.actorType() != null ? command.actorType() : ActorType.SERVICE;
        String traceId = command.traceId() != null ? command.traceId() : traceIds.current();

        entityManager.createNativeQuery(
            "INSERT INTO audit_event (id, occurred_at, recorded_at, actor, actor_type, action, " +
            "target_type, target_id, outcome, source, source_event_id, client_net, trace_id, detail) " +
            "VALUES (:id, :occurredAt, now(), :actor, :actorType, :action, " +
            ":targetType, :targetId, :outcome, :source, :sourceEventId, :clientNet, :traceId, " +
            "cast(:detail as jsonb)) " +
            "ON CONFLICT (source_event_id) WHERE source_event_id IS NOT NULL DO NOTHING")
            .setParameter("id", command.id())
            .setParameter("occurredAt", occurredAt)
            .setParameter("actor", command.actor())
            .setParameter("actorType", actorType.name())
            .setParameter("action", command.action() != null ? command.action().name() : null)
            .setParameter("targetType", targetType)
            .setParameter("targetId", command.targetId())
            .setParameter("outcome", command.outcome() != null ? command.outcome().name() : null)
            .setParameter("source", source)
            .setParameter("sourceEventId", command.sourceEventId())
            .setParameter("clientNet", command.clientNet())
            .setParameter("traceId", traceId)
            .setParameter("detail", toJson(detail))
            .executeUpdate();
    }

    private String toJson(Map<String, Object> detail) {
        try {
            return objectMapper.writeValueAsString(detail);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}