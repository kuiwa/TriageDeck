package com.triagedeck.alert.service;

import com.triagedeck.alert.api.AlertClearController.AlertClearResponse;
import com.triagedeck.platform.audit.Auditable;
import com.triagedeck.platform.security.SecurityContext;
import com.triagedeck.alert.repo.AlertHistoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
public class AlertClearService {

    private final AlertHistoryRepository historyRepo;
    private final SecurityContext securityContext;

    public AlertClearService(
            AlertHistoryRepository historyRepo,
            SecurityContext securityContext) {
        this.historyRepo = historyRepo;
        this.securityContext = securityContext;
    }

    @Transactional
    @Auditable(action = "ALERT_CLEARED", targetType = "ALERT", targetIdParam = "id")
    public AlertClearResponse clear(UUID id, String reason) {
        // Atomic move per LLD 02-alert §3.2: insert into history ... on conflict do nothing,
        // then delete from active. 0 rows moved means already cleared or never existed.
        boolean moved = historyRepo.clear(id, securityContext.getCurrentUser(), reason);
        if (!moved) {
            if (historyRepo.existsById(id)) {
                throw new AlertAlreadyClearedException(id);
            }
            throw new AlertNotFoundException(id);
        }

        return new AlertClearResponse(true);
    }

    public static class AlertNotFoundException extends RuntimeException {
        public AlertNotFoundException(UUID id) {
            super("Alert not found: " + id);
        }
    }

    public static class AlertAlreadyClearedException extends RuntimeException {
        public AlertAlreadyClearedException(UUID id) {
            super("Alert already cleared: " + id);
        }
    }
}