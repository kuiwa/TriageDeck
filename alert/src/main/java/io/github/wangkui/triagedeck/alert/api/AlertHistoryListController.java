package io.github.wangkui.triagedeck.alert.api;

import io.github.wangkui.triagedeck.alert.repo.AlertHistoryRepository;
import io.github.wangkui.triagedeck.platform.security.RequirePermission;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
public class AlertHistoryListController {

    private final AlertHistoryRepository historyRepo;

    public AlertHistoryListController(AlertHistoryRepository historyRepo) {
        this.historyRepo = historyRepo;
    }

    @GetMapping("/api/alerts/history")
    @RequirePermission(obj = "alert", act = "read")
    public ResponseEntity<AlertHistoryPage> history(
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String clearedBy,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {

        // Clip size to max 100 per §0.2 & §1.6
        if (size > 100) {
            size = 100;
        }
        if (size < 1) {
            return ResponseEntity.badRequest()
                    .body(new AlertHistoryPage(Collections.emptyList(), 0, page, 0));
        }

        // Build where conditions
        Map<String, Object> params = new HashMap<>();
        StringBuilder where = new StringBuilder("WHERE 1=1 ");

        if (severity != null) {
            where.append("AND severity = :severity ");
            params.put("severity", severity);
        }
        if (source != null) {
            where.append("AND source = :source ");
            params.put("source", source);
        }
        if (clearedBy != null) {
            where.append("AND cleared_by = :clearedBy ");
            params.put("clearedBy", clearedBy);
        }
        if (keyword != null) {
            where.append("AND message LIKE :keyword ");
            params.put("keyword", "%" + keyword + "%");
        }
        if (from != null) {
            // from/to act on clearedAt
            where.append("AND cleared_at >= :from ");
            params.put("from", from);
        }
        if (to != null) {
            where.append("AND cleared_at <= :to ");
            params.put("to", to);
        }

        // Count query - use simple count with where
        String countSql = "SELECT COUNT(*) FROM alerts_history " + where;
        long total = historyRepo.getCount(countSql, params);

        // Data query with pagination
        int offset = page * size;
        String dataSql = "SELECT id, severity, source, type, resource_id, event_code, "
                + "message, first_seen_at, last_seen_at, occurrence_count, fingerprint, "
                + "cleared_at, cleared_by, cleared_reason, clear_mode "
                + "FROM alerts_history " + where
                + "ORDER BY cleared_at DESC, id DESC "
                + "LIMIT :size OFFSET :offset";

        params.put("size", size);
        params.put("offset", offset);

        List<Map<String, Object>> rows = historyRepo.query(dataSql, params);

        List<AlertHistoryView> items = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            items.add(mapToAlertHistoryView(row));
        }

        AlertHistoryPage result = new AlertHistoryPage(items, total, page, size);
        return ResponseEntity.ok(result);
    }

    private AlertHistoryView mapToAlertHistoryView(Map<String, Object> row) {
        AlertView.AlertViewBuilder builder = AlertView.builder();
        builder.id(UUID.fromString(row.get("id").toString()));
        builder.severity(io.github.wangkui.triagedeck.alert.domain.Severity.valueOf(row.get("severity").toString()));
        builder.source(row.get("source").toString());
        builder.type(row.get("type").toString());
        builder.resourceId(row.get("resource_id").toString());
        builder.eventCode(row.get("event_code").toString());
        builder.message(row.get("message").toString());
        builder.firstSeenAt(((java.sql.Timestamp) row.get("first_seen_at")).toInstant());
        builder.lastSeenAt(((java.sql.Timestamp) row.get("last_seen_at")).toInstant());
        builder.occurrenceCount((long) row.get("occurrence_count"));
        builder.fingerprint(row.get("fingerprint").toString());

        AlertHistoryView.AlertHistoryViewBuilder hbuilder = AlertHistoryView.builder();
        hbuilder.id(builder.id());
        hbuilder.severity(builder.severity());
        hbuilder.source(builder.source());
        hbuilder.type(builder.type());
        hbuilder.resourceId(builder.resourceId());
        hbuilder.eventCode(builder.eventCode());
        hbuilder.message(builder.message());
        hbuilder.firstSeenAt(builder.firstSeenAt());
        hbuilder.lastSeenAt(builder.lastSeenAt());
        hbuilder.occurrenceCount(builder.occurrenceCount());
        hbuilder.fingerprint(builder.fingerprint());
        hbuilder.clearedAt(((java.sql.Timestamp) row.get("cleared_at")).toInstant());
        hbuilder.clearedBy(row.get("cleared_by").toString());
        hbuilder.clearedReason(row.get("cleared_reason") != null ? row.get("cleared_reason").toString() : null);
        hbuilder.clearMode(row.get("clear_mode").toString());

        return hbuilder.build();
    }
}