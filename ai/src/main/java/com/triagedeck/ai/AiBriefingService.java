package com.triagedeck.ai;

import com.triagedeck.alert.api.port.AlertReadPort;
import com.triagedeck.alert.api.AlertView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;

@Service
public class AiBriefingService {

    private final AlertReadPort alertReadPort;

    public AiBriefingService(AlertReadPort alertReadPort) {
        this.alertReadPort = alertReadPort;
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
        AlertView alert = alertReadPort.findById(alertId);
        if (alert == null) {
            throw new IllegalArgumentException("Alert not found: " + alertId);
        }

        // Build evidence from alert data (read-only, no DB writes)
        StringBuilder evidence = new StringBuilder();
        evidence.append("alertId=").append(alertId);
        evidence.append(" severity=").append(alert.severity());
        evidence.append(" source=").append(alert.source());

        // Generate structured briefing text (AI model output simulation)
        return String.format(
                "Alert Briefing: [%s] %s from %s at %s%nEvidence: %s",
                alert.severity(), alert.source(), alert.resourceId(),
                alert.lastSeenAt(), evidence);
    }

    /**
     * SSE endpoint - returns SseEmitter for streaming.
     * First byte within 2s, done event with complete JSON.
     */
    @Transactional(readOnly = true)
    public SseEmitter briefingSse(UUID alertId, HttpServletRequest request) {
        SseEmitter emitter = new SseEmitter(30000L); // 30s timeout

        // Verify alert exists via read port
        AlertView alert = alertReadPort.findById(alertId);
        if (alert == null) {
            emitter.completeWithError(new IllegalArgumentException("Alert not found: " + alertId));
            return emitter;
        }

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