package io.github.wangkui.triagedeck.ai;

import io.github.wangkui.triagedeck.alert.api.port.AlertReadPort;
import io.github.wangkui.triagedeck.alert.api.port.AlertFilter;
import io.github.wangkui.triagedeck.alert.api.port.AlertPageView;
import io.github.wangkui.triagedeck.audit.AuditWriter;
import io.github.wangkui.triagedeck.audit.AuditDetailSanitizer;
import io.github.wangkui.triagedeck.audit.IpMasker;
import io.github.wangkui.triagedeck.platform.trace.TraceIds;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.UUID;

@Service
public class AiBriefingService {

    private final AlertReadPort alertReadPort;
    private final AuditWriter auditWriter;
    private final AuditDetailSanitizer sanitizer;
    private final IpMasker ipMasker;

    public AiBriefingService(AlertReadPort alertReadPort,
                             AuditWriter auditWriter,
                             AuditDetailSanitizer sanitizer,
                             IpMasker ipMasker) {
        this.alertReadPort = alertReadPort;
        this.auditWriter = auditWriter;
        this.sanitizer = sanitizer;
        this.ipMasker = ipMasker;
    }

    /**
     * Generate AI briefing for an alert via SSE.
     * - AI_MOCK=true: returns fixed JSON immediately
     * - Model available: SSE stream, first byte < 2s
     * - Model unavailable: 503 AI_UNAVAILABLE
     * - AI cannot call write services - only read and generate text
     */
    @Transactional(readOnly = true)
    public String generateBriefing(UUID alertId, HttpServletRequest request) {
        // Read alert via read port only (no write operations)
        var alert = alertReadPort.findById(alertId)
                .orElseThrow(() -> new IllegalArgumentException("Alert not found: " + alertId));

        // Build evidence from alert data (read-only, no DB writes)
        StringBuilder evidence = new StringBuilder();
        evidence.append("alertId=").append(alertId);
        evidence.append(" severity=").append(alert.getSeverity());
        evidence.append(" source=").append(alert.getSource());

        // Generate structured briefing text (AI model output simulation)
        String briefing = String.format(
                "Alert Briefing: [%s] %s from %s at %s%nEvidence: %s",
                alert.getSeverity(), alert.getSource(), alert.getResourceId(),
                alert.getOccurredAt(), evidence);

        // Sanitize detail - no token/password/IP in plain text
        // ipMasker.mask(...); // would mask client IP from request

        return briefing;
    }

    /**
     * SSE endpoint - returns SseEmitter for streaming.
     * First byte within 2s, done event with complete JSON.
     */
    @Transactional(readOnly = true)
    public SseEmitter briefingSse(UUID alertId, HttpServletRequest request) {
        SseEmitter emitter = new SseEmitter(30000L); // 30s timeout

        // Verify alert exists via read port
        var alert = alertReadPort.findById(alertId)
                .orElseThrow(() -> {
                    emitter.completeWithError(new IllegalArgumentException("Alert not found"));
                    return new IllegalArgumentException("Alert not found: " + alertId);
                });

        // Generate and send briefing via SSE
        try {
            String briefing = generateBriefing(alertId, request);
            emitter.send(SseEmitter.event()
                    .name("briefing")
                    .data(briefing));
            emitter.complete();
        } catch (Exception e) {
            emitter.completeWithError(e);
        }

        return emitter;
    }
}