package com.triagedeck.alert.service;

import com.triagedeck.alert.api.AlertClearController.AlertClearResponse;
import com.triagedeck.alert.repo.AlertHistoryRepository;
import com.triagedeck.alert.repo.AlertHistoryRepository.ClearedAlert;
import com.triagedeck.platform.audit.Auditable;
import com.triagedeck.platform.event.AlertClearedMessage;
import com.triagedeck.platform.event.DomainEventBus;
import com.triagedeck.platform.security.SecurityContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;
import java.util.UUID;

@Service
public class AlertClearService {

    private final AlertHistoryRepository historyRepo;
    private final SecurityContext securityContext;
    private final DomainEventBus domainEventBus;

    public AlertClearService(
            AlertHistoryRepository historyRepo,
            SecurityContext securityContext,
            DomainEventBus domainEventBus) {
        this.historyRepo = historyRepo;
        this.securityContext = securityContext;
        this.domainEventBus = domainEventBus;
    }

    @Transactional
    @Auditable(action = "ALERT_CLEARED", targetType = "ALERT", targetIdParam = "id")
    public AlertClearResponse clear(UUID id, String reason) {
        // Atomic move per LLD 02-alert §3.2: insert into history ... on conflict do nothing,
        // then delete from active. 0 rows moved means already cleared or never existed.
        String actor = securityContext.getCurrentUser();
        Optional<ClearedAlert> moved = historyRepo.clear(id, actor, reason);
        if (moved.isEmpty()) {
            if (historyRepo.existsById(id)) {
                throw new AlertAlreadyClearedException(id);
            }
            throw new AlertNotFoundException(id);
        }

        ClearedAlert cleared = moved.get();
        // 只有主事务提交后才发（02-design §5.3）；回滚则一条都不发
        domainEventBus.publishAfterCommit(new AlertClearedMessage(
                cleared.id(),
                cleared.fingerprint(),
                cleared.severity(),
                cleared.source(),
                cleared.type(),
                cleared.resourceId(),
                cleared.occurrenceCount(),
                cleared.clearedAt(),
                actor != null && !actor.isBlank() ? actor : "SYSTEM"));

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