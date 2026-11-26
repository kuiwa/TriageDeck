package com.triagedeck.alert.repo;

import com.triagedeck.alert.api.AlertHistoryView;
import com.triagedeck.alert.api.AlertView;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.Optional;

@Repository
public class AlertHistoryRepository {

    private static final String CLEAR_SQL = """
            with moved as (
              insert into alerts_history (
                id, fingerprint, source, type, resource_id, event_code, severity,
                message, first_seen_at, last_seen_at, occurrence_count,
                latest_payload, latest_event_id, created_at, updated_at,
                cleared_at, cleared_by, cleared_reason, clear_mode
              )
              select a.id, a.fingerprint, a.source, a.type, a.resource_id, a.event_code, a.severity,
                     a.message, a.first_seen_at, a.last_seen_at, a.occurrence_count,
                     a.latest_payload, a.latest_event_id, a.created_at, a.updated_at,
                     now(), :clearedBy, :clearedReason, 'MANUAL'
              from alerts_active a
              where a.id = :id
              on conflict (id) do nothing
              returning id
            )
            delete from alerts_active a using moved m where a.id = m.id
            returning a.id
            """;

    private static final String EXISTS_BY_ID_SQL =
            "SELECT count(*) FROM alerts_history WHERE id = :id";

    private static final String SELECT_BY_ID_SQL =
            "SELECT id, severity, source, type, resource_id, event_code, message, "
            + "first_seen_at, last_seen_at, occurrence_count, fingerprint, "
            + "cleared_at, cleared_by, cleared_reason, clear_mode "
            + "FROM alerts_history WHERE id = :id";

    private final NamedParameterJdbcTemplate jdbc;

    public AlertHistoryRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Atomically moves the active alert to history (LLD 02-alert §3.2).
     * Returns true when a row was moved; false when nothing moved
     * (id absent from active or already present in history).
     */
    public boolean clear(UUID id, String actor, String reason) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("clearedBy", actor != null && !actor.isBlank() ? actor : "SYSTEM")
                .addValue("clearedReason", reason);

        List<UUID> moved = jdbc.query(CLEAR_SQL, params,
                (rs, rowNum) -> rs.getObject("id", UUID.class));
        return !moved.isEmpty();
    }

    public boolean existsById(UUID id) {
        Long count = jdbc.queryForObject(EXISTS_BY_ID_SQL,
                new MapSqlParameterSource("id", id), Long.class);
        return count != null && count > 0;
    }

    public Optional<AlertView> findById(UUID id) {
        try {
            List<AlertView> rows = jdbc.query(SELECT_BY_ID_SQL,
                    new MapSqlParameterSource("id", id),
                    (rs, rowNum) -> AlertView.builder()
                            .id(UUID.fromString(rs.getString("id")))
                            .severity(com.triagedeck.alert.domain.Severity.valueOf(rs.getString("severity")))
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