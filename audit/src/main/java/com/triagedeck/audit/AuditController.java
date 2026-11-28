package com.triagedeck.audit.api;

import com.triagedeck.audit.AuditDetailSanitizer;
import com.triagedeck.audit.CollectorState;
import com.triagedeck.audit.repo.AuditRepository;
import com.triagedeck.audit.AuditWriter;
import com.triagedeck.platform.audit.ActorType;
import com.triagedeck.platform.audit.AuditAction;
import com.triagedeck.platform.audit.Outcome;
import com.triagedeck.platform.audit.TargetType;
import com.triagedeck.platform.security.RequirePermission;
import com.triagedeck.platform.trace.TraceIds;
import com.triagedeck.platform.security.SecurityContext;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.triagedeck.audit.AuditDetailSanitizer.sanitize;

@RestController
@RequestMapping("/api/audit")
public class AuditController {

    private static final int MAX_EXPORT_ROWS = 50000;
    private static final int MAX_SPAN_DAYS = 90;
    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;

    private final AuditRepository auditRepository;
    private final CollectorState collectorState;
    private final AuditWriter auditWriter;
    private final TraceIds traceIds;
    private final SecurityContext securityContext;

    @Value("${app.business-zone:Asia/Shanghai}")
    private String businessZone;

    public AuditController(AuditRepository auditRepository,
                           CollectorState collectorState,
                           AuditWriter auditWriter,
                           TraceIds traceIds,
                           SecurityContext securityContext) {
        this.auditRepository = auditRepository;
        this.collectorState = collectorState;
        this.auditWriter = auditWriter;
        this.traceIds = traceIds;
        this.securityContext = securityContext;
    }

    @GetMapping("/events")
    @RequirePermission(obj = "audit", act = "read")
    public ResponseEntity<Map<String, Object>> listEvents(
            @RequestParam("from") String from,
            @RequestParam(value = "to", required = false) String toStr,
            @RequestParam(value = "actor", required = false) String actor,
            @RequestParam(value = "action", required = false) List<String> actions,
            @RequestParam(value = "outcome", required = false) List<String> outcomes,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {

        String traceId = traceIds.current();
        Instant fromInstant;
        Instant to;
        try {
            fromInstant = parseInstantTolerant(from);
            to = (toStr != null && !toStr.isBlank()) ? parseInstantTolerant(toStr) : null;
        } catch (DateTimeParseException e) {
            return validationError("时间格式不合法，应为 ISO-8601（如 2026-11-21T05:54:00Z）");
        }

        Instant maxTo = fromInstant.plus(MAX_SPAN_DAYS, ChronoUnit.DAYS);
        if (to != null && to.isAfter(maxTo)) {
            to = maxTo;
        }

        String fromNorm = fromInstant.toString();
        List<AuditEventView> items = auditRepository.findByCriteria(fromNorm, to, actor, actions, outcomes, page, size);
        int total = auditRepository.countByCriteria(fromNorm, to, actor, actions, outcomes);

        List<Map<String, Object>> sanitizedItems = items.stream()
                .map(e -> {
                    Map<String, Object> detail = sanitize(e.detail());
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", e.id());
                    item.put("occurredAt", e.occurredAt().toString());
                    item.put("actor", e.actor());
                    item.put("actorType", e.actorType());
                    item.put("action", e.action());
                    item.put("targetType", e.targetType());
                    item.put("targetId", e.targetId());
                    item.put("outcome", e.outcome());
                    item.put("source", e.source());
                    item.put("clientNet", e.clientNet());
                    item.put("traceId", e.traceId());
                    item.put("detail", detail);
                    return item;
                })
                .collect(Collectors.toList());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("items", sanitizedItems);
        response.put("total", total);
        response.put("page", page);
        response.put("size", size);
        response.put("eventsCollected", collectorState.isEventsCollected());
        response.put("eventsDisabled", collectorState.isEventsDisabled());
        response.put("collectorLastRunAt",
                collectorState.getCollectorLastRunAt() != null
                        ? collectorState.getCollectorLastRunAt().toString()
                        : null);

        return ResponseEntity.ok(response);
    }

    @GetMapping(value = "/events/export", produces = MediaType.TEXT_PLAIN_VALUE)
    @RequirePermission(obj = "audit", act = "export")
    public void export(
            @RequestParam("from") String from,
            @RequestParam(value = "to", required = false) String toStr,
            @RequestParam(value = "actor", required = false) String actor,
            @RequestParam(value = "action", required = false) List<String> actions,
            @RequestParam(value = "outcome", required = false) List<String> outcomes,
            @RequestParam(value = "format", defaultValue = "csv") String format,
            HttpServletResponse response) throws Exception {

        String traceId = traceIds.current();
        Instant fromInstant;
        Instant to;
        try {
            fromInstant = parseInstantTolerant(from);
            to = (toStr != null && !toStr.isBlank()) ? parseInstantTolerant(toStr) : null;
        } catch (DateTimeParseException e) {
            writeJsonError(response, HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                    "时间格式不合法，应为 ISO-8601（如 2026-11-21T05:54:00Z）", traceId);
            return;
        }

        Instant maxTo = fromInstant.plus(MAX_SPAN_DAYS, ChronoUnit.DAYS);
        if (to != null && to.isAfter(maxTo)) {
            to = maxTo;
        }

        String fromNorm = fromInstant.toString();
        int count = auditRepository.countForExportLimit(fromNorm, to, actor, actions, outcomes);
        if (count > MAX_EXPORT_ROWS) {
            writeJsonError(response, HttpStatus.UNPROCESSABLE_ENTITY, "EXPORT_TOO_LARGE",
                    "导出行数超过 " + MAX_EXPORT_ROWS + " 限制", traceId);
            return;
        }

        List<AuditEventView> items = auditRepository.findByCriteria(fromNorm, to, actor, actions, outcomes, 0, Integer.MAX_VALUE);

        response.setStatus(HttpStatus.OK.value());
        response.setContentType("text/csv; charset=utf-8");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        String fileName = "audit-" + fromInstant.atZone(ZoneOffset.UTC).toLocalDate()
                + "-" + (to != null ? to.atZone(ZoneOffset.UTC).toLocalDate() : "now") + ".csv";
        response.setHeader("Content-Disposition", "attachment; filename=\"" + fileName + "\"");

        response.getOutputStream().write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});

        PrintWriter writer = new PrintWriter(response.getOutputStream(), true, StandardCharsets.UTF_8);

        writer.println("发生时间,操作人,操作人类型,动作,对象类型,对象,结果,来源,客户端网段,详情摘要");

        int countWritten = 0;
        for (AuditEventView e : items) {
            if (countWritten >= MAX_EXPORT_ROWS) break;
            writer.println(escapeCsv(e.occurredAt().toString()) + ","
                    + escapeCsv(e.actor()) + ","
                    + escapeCsv(e.actorType()) + ","
                    + escapeCsv(e.action()) + ","
                    + escapeCsv(e.targetType()) + ","
                    + escapeCsv(e.targetId() != null ? e.targetId() : "") + ","
                    + escapeCsv(e.outcome()) + ","
                    + escapeCsv(e.source()) + ","
                    + escapeCsv(e.clientNet() != null ? e.clientNet() : "") + ","
                    + escapeCsv(detailToSummary(e.detail())));
            countWritten++;
        }
        writer.flush();
    }

    private String detailToSummary(Map<String, Object> detail) {
        if (detail == null || detail.isEmpty()) {
            return "";
        }
        Map<String, Object> sanitized = sanitize(detail);
        return sanitized.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + String.valueOf(entry.getValue()))
                .collect(Collectors.joining("; "))
                .replace("\n", " ")
                .replace("\r", " ");
    }

    private String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    /**
     * 容错解析时间：支持完整 ISO instant（...Z）、带偏移（+08:00）、以及
     * datetime-local（无时区，按 UTC 处理）。
     */
    static Instant parseInstantTolerant(String value) {
        if (value == null || value.isBlank()) {
            throw new DateTimeParseException("blank instant", "", 0);
        }
        String v = value.trim();
        try {
            return Instant.parse(v);
        } catch (DateTimeParseException ignored) {
            try {
                return java.time.OffsetDateTime.parse(v).toInstant();
            } catch (DateTimeParseException ignored2) {
                return java.time.LocalDateTime.parse(v)
                        .atZone(ZoneOffset.UTC)
                        .toInstant();
            }
        }
    }

    private ResponseEntity<Map<String, Object>> validationError(String message) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", "VALIDATION_FAILED");
        error.put("message", message);
        error.put("traceId", traceIds.current());
        return ResponseEntity.badRequest().body(error);
    }

    private void writeJsonError(HttpServletResponse response, HttpStatus status,
                                String code, String message, String traceId) throws Exception {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", code);
        error.put("message", message);
        error.put("traceId", traceId);
        new com.fasterxml.jackson.databind.ObjectMapper().writeValue(response.getWriter(), error);
    }
}