package io.github.wangkui.triagedeck.alert.repo;

import io.github.wangkui.triagedeck.alert.api.AlertHistoryView;
import io.github.wangkui.triagedeck.alert.api.AlertView;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.Optional;

@Repository
public class AlertHistoryRepository {

    private static final String INSERT_CLEARED_SQL =
            "INSERT INTO alerts_history "
            + "(id, fingerprint, severity, source, type, resource_id, event_code, "
            + "message, first_seen_at, last_seen_at, occurrence_count, latest_payload, "
            + "latest_event_id, cleared_at, cleared_by, cleared_reason, clear_mode) "
            + "VALUES "
            + "(:id, :fingerprint, :severity, :source, :type, :resourceId, :eventCode, "
            + ":message, :firstSeenAt, :lastSeenAt, :occurrenceCount, :latestPayload, "
            + ":latestEventId, now(), :clearedBy, :clearedReason, :clearMode)";

    private static final String SELECT_BY_ID_SQL =
            "SELECT id, severity, source, type, resource_id, event_code, message, "
            + "first_seen_at, last_seen_at, occurrence_count, fingerprint, "
            + "cleared_at, cleared_by, cleared_reason, clear_mode "
            + "FROM alerts_history WHERE id = :id";

    private final NamedParameterJdbcTemplate jdbc;

    public AlertHistoryRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean insertCleared(
            UUID id,
            String fingerprint,
            String severity,
            String source,
            String type,
            String resourceId,
            String eventCode,
            String message,
            Object firstSeenAt,
            Object lastSeenAt,
            long occurrenceCount,
            Object latestPayload,
            UUID latestEventId,
            String actor,
            String traceId,
            String reason) {

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("fingerprint", fingerprint)
                .addValue("severity", severity)
                .addValue("source", source)
                .addValue("type", type)
                .addValue("resourceId", resourceId)
                .addValue("eventCode", eventCode)
                .addValue("message", message)
                .addValue("firstSeenAt", firstSeenAt)
                .addValue("lastSeenAt", lastSeenAt)
                .addValue("occurrenceCount", occurrenceCount)
                .addValue("latestPayload", latestPayload != null ? latestPayload : "{}")
                .addValue("latestEventId", latestEventId)
                .addValue("clearedBy", actor != null ? actor : "SYSTEM")
                .addValue("clearedReason", reason != null ? reason : "")
                .addValue("clearMode", "MANUAL");

        try {
            jdbc.update(INSERT_CLEARED_SQL, params);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public Optional<AlertView> findById(UUID id) {
        try {
            List<AlertView> rows = jdbc.query(SELECT_BY_ID_SQL,
                    new MapSqlParameterSource("id", id),
                    (rs, rowNum) -> AlertView.builder()
                            .id(UUID.fromString(rs.getString("id")))
                            .severity(io.github.wangkui.triagedeck.alert.domain.Severity.valueOf(rs.getString("severity")))
                            .source(rs.getString("source"))
                            .type(rs.getString("type"))
                            .resourceId(rs.getString("resource_id"))
                            .eventCode(rs.getString("event_code"))
                            .message(rs.getString("message"))
                            .firstSeenAt(rs.getTimestamp("first_seen_at").toInstant())
                            .lastSeenAt(rs.getTimestamp("last_seen_at").toInstant())
                            .occurrenceCount(rs.getLong("occurrence_count"))
                            .fingerprint(rs.getString("fingerprint"))
                            .build());
            return rows.stream().findFirst();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /** Get count with custom WHERE clause. */
    public long getCount(String whereClause, Map<String, Object> params) {
        String sql = String.format("SELECT COUNT(*) FROM alerts_history %s", whereClause);
        return jdbc.queryForObject(sql, params, Long.class);
    }

    /** Execute arbitrary query with named parameters. */
    public List<Map<String, Object>> query(String sql, Map<String, Object> params) {
        return jdbc.queryForList(sql, params);
    }
}