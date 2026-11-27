package com.triagedeck.alert.service;

import com.triagedeck.alert.api.AlertDetailView;
import com.triagedeck.alert.api.AlertEventView;
import com.triagedeck.alert.api.ClearedInfo;
import com.triagedeck.alert.api.MergeFields;
import com.triagedeck.alert.domain.Severity;
import com.triagedeck.alert.repo.AlertEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 告警详情查询：先查 {@code alerts_active}，未命中再查 {@code alerts_history}；
 * 关联 {@code alert_event} 最近 20 条作为证据链。只读事务。
 */
@Service
public class AlertDetailService {

    private static final String FIND_ACTIVE_SQL =
            "SELECT id, severity, source, type, resource_id, event_code, message, "
                    + "first_seen_at, last_seen_at, occurrence_count, fingerprint, "
                    + "latest_payload::text as latest_payload "
                    + "FROM alerts_active WHERE id = :id";

    private static final String FIND_HISTORY_SQL =
            "SELECT id, severity, source, type, resource_id, event_code, message, "
                    + "first_seen_at, last_seen_at, occurrence_count, fingerprint, "
                    + "latest_payload::text as latest_payload, "
                    + "cleared_at, cleared_by, cleared_reason, clear_mode "
                    + "FROM alerts_history WHERE id = :id";

    private final NamedParameterJdbcTemplate jdbc;
    private final AlertEventRepository eventRepository;
    private final ObjectMapper objectMapper;

    public AlertDetailService(NamedParameterJdbcTemplate jdbc,
                              AlertEventRepository eventRepository,
                              ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.eventRepository = eventRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public Optional<AlertDetailView> findDetail(UUID id) {
        Optional<AlertDetailView> active = findBySql(FIND_ACTIVE_SQL, id, "ACTIVE", false);
        if (active.isPresent()) {
            return active;
        }
        return findBySql(FIND_HISTORY_SQL, id, "CLEARED", true);
    }

    private Optional<AlertDetailView> findBySql(String sql, UUID id, String status, boolean hasCleared) {
        List<AlertDetailView> rows = jdbc.query(sql, new MapSqlParameterSource("id", id),
                (rs, rowNum) -> mapRow(rs, status, hasCleared));
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        AlertDetailView base = rows.get(0);
        List<AlertEventView> events = eventRepository.findRecentByAlertId(base.id());
        return Optional.of(new AlertDetailView(
                base.id(), base.status(), base.severity(), base.source(), base.type(),
                base.resourceId(), base.eventCode(), base.message(),
                base.firstSeenAt(), base.lastSeenAt(), base.occurrenceCount(), base.fingerprint(),
                base.mergeFields(), base.mergeRule(), base.latestPayload(),
                events, base.cleared()
        ));
    }

    private AlertDetailView mapRow(ResultSet rs, String status, boolean hasCleared) throws SQLException {
        MergeFields mergeFields = new MergeFields(
                rs.getString("source"),
                rs.getString("type"),
                rs.getString("resource_id"),
                rs.getString("event_code")
        );
        ClearedInfo cleared = null;
        if (hasCleared) {
            cleared = new ClearedInfo(
                    tsToInstant(rs, "cleared_at"),
                    rs.getString("cleared_by"),
                    rs.getString("cleared_reason"),
                    rs.getString("clear_mode")
            );
        }
        return AlertDetailView.builder()
                .id(UUID.fromString(rs.getString("id")))
                .status(status)
                .severity(Severity.valueOf(rs.getString("severity")))
                .source(rs.getString("source"))
                .type(rs.getString("type"))
                .resourceId(rs.getString("resource_id"))
                .eventCode(rs.getString("event_code"))
                .message(rs.getString("message"))
                .firstSeenAt(rs.getTimestamp("first_seen_at").toInstant())
                .lastSeenAt(rs.getTimestamp("last_seen_at").toInstant())
                .occurrenceCount(rs.getLong("occurrence_count"))
                .fingerprint(rs.getString("fingerprint"))
                .mergeFields(mergeFields)
                .mergeRule("fingerprint")
                .latestPayload(readPayload(rs.getObject("latest_payload")))
                .events(List.of())
                .cleared(cleared)
                .build();
    }

    private Instant tsToInstant(ResultSet rs, String column) throws SQLException {
        java.sql.Timestamp ts = rs.getTimestamp(column);
        return ts == null ? null : ts.toInstant();
    }

    private Object readPayload(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof String s) {
            try {
                return objectMapper.readTree(s);
            } catch (Exception ignored) {
                return s;
            }
        }
        return raw;
    }
}