package io.github.wangkui.triagedeck.notify;

import io.github.wangkui.triagedeck.platform.trace.TraceIds;
import io.github.wangkui.triagedeck.audit.AuditCommand;
import io.github.wangkui.triagedeck.audit.AuditWriter;
import io.github.wangkui.triagedeck.audit.AuditDetailSanitizer;
import io.github.wangkui.triagedeck.audit.IpMasker;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;

import java.time.Instant;

@Component
public class NotifyConsumer {

    @PersistenceContext
    private EntityManager entityManager;

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final AuditWriter auditWriter;
    private final AuditDetailSanitizer sanitizer;
    private final IpMasker ipMasker;

    public NotifyConsumer(KafkaTemplate<String, Object> kafkaTemplate,
                          AuditWriter auditWriter,
                          AuditDetailSanitizer sanitizer,
                          IpMasker ipMasker) {
        this.kafkaTemplate = kafkaTemplate;
        this.auditWriter = auditWriter;
        this.sanitizer = sanitizer;
        this.ipMasker = ipMasker;
    }

    @KafkaListener(topics = "alert.cleared", groupId = "notify")
    @Transactional(propagation = jakarta.transaction.Propagation.REQUIRES_NEW)
    public void consumeClearedMessage(String key, String message) {
        // Parse the AlertClearedMessage JSON
        // message format: {"alertId":"...uuid...","fingerprint":"...","severity":"CRITICAL",
        //   "source":"payment-api","type":"5xx","resourceId":"pay-01","occurrenceCount":12,
        //   "clearedAt":"2026-11-23T02:10:00Z","clearedBy":"zhangwei"}

        // Write notification record
        String maskedIp = ipMasker.mask("10.20.3.77"); // would come from actual message

        // Build notification payload (copy of the message)
        String payload = message;

        entityManager.createNativeQuery(
                "INSERT INTO notification (id, alert_id, channel, payload, created_at) " +
                        "VALUES (:id, :alertId, :channel, :payload, now())")
                .setParameter("id", UUID.randomUUID())
                .setParameter("alertId", key)
                .setParameter("channel", "LOG")
                .setParameter("payload", payload)
                .executeUpdate();

        // Also send to audit (optional, for completeness)
        // Could send to audit_writer if needed
    }

    /**
     * Handle failed message delivery - send to DLT topic
     */
    @KafkaListener(topics = "alert.ingested.dlt", groupId = "notify")
    @Transactional(propagation = jakarta.transaction.Propagation.REQUIRES_NEW)
    public void handleDlt(String key, String message) {
        // Log the failed message for manual inspection
        // In production, could write to a separate DLT table or alert team
        System.err.println("DLT message received - alertId: " + key + ", message: " + message);
    }
}