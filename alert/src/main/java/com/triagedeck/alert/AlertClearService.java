package com.triagedeck.alert.service;

import com.triagedeck.alert.api.AlertView;
import com.triagedeck.alert.api.AlertClearController.AlertClearResponse;
import com.triagedeck.platform.audit.Auditable;
import com.triagedeck.platform.security.SecurityContext;
import com.triagedeck.platform.trace.TraceIds;
import com.triagedeck.alert.repo.AlertActiveRepository;
import com.triagedeck.alert.repo.AlertHistoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
import java.util.Optional;

@Service
public class AlertClearService {

    private final AlertActiveRepository activeRepo;
    private final AlertHistoryRepository historyRepo;
    private final SecurityContext securityContext;
    private final TraceIds traceIds;

    public AlertClearService(
            AlertActiveRepository activeRepo,
            AlertHistoryRepository historyRepo,
            SecurityContext securityContext,
            TraceIds traceIds) {
        this.activeRepo = activeRepo;
        this.historyRepo = historyRepo;
        this.securityContext = securityContext;
        this.traceIds = traceIds;
    }

    @Transactional
    @Auditable(action = "ALERT_CLEARED", targetType = "ALERT", targetIdParam = "id")
    public AlertClearResponse clear(UUID id, String reason) {
        // 1. Try to find the alert in active first to give 404 if not found
        Optional<AlertView> activeOpt = activeRepo.findById(id);
        if (activeOpt.isEmpty()) {
            // Alert not found in active - will become 404 in controller
            // But we need to throw so controller can handle it
            throw new AlertNotFoundException(id);
        }

        AlertView active = activeOpt.get();

        // 2. Try to insert into history (atomically within same transaction)
        boolean inserted = historyRepo.insertCleared(
                id,
                active.fingerprint(),
                active.severity().name(),
                active.source(),
                active.type(),
                active.resourceId(),
                active.eventCode(),
                active.message(),
                active.firstSeenAt(),
                active.lastSeenAt(),
                active.occurrenceCount(),
                "{}", // latest_payload: empty JSON object for clear
                active.id(), // latest_event_id - reuse alert id for now
                securityContext.getCurrentUser(),
                traceIds.current(),
                reason);

        if (!inserted) {
            // Id already exists in history → already cleared
            return new AlertClearResponse(false);
        }

        // 3. Delete from active
        activeRepo.deleteById(id);

        return new AlertClearResponse(true);
    }

    public static class AlertNotFoundException extends RuntimeException {
        public AlertNotFoundException(UUID id) {
            super("Alert not found: " + id);
        }
    }
}