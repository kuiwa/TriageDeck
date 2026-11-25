package io.github.wangkui.triagedeck.audit.repo;

import io.github.wangkui.triagedeck.audit.api.AuditEventView;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class AuditRepository {

    private final JdbcTemplate jdbcTemplate;

    public AuditRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final RowMapper<AuditEventView> ROW_MAPPER = (rs, rowNum) -> new AuditEventView(
            UUID.fromString(rs.getString("id")),
            rs.getTimestamp("occurred_at").toInstant(),
            rs.getString("actor"),
            rs.getString("actor_type"),
            rs.getString("action"),
            rs.getString("target_type"),
            rs.getString("target_id"),
            rs.getString("outcome"),
            rs.getString("source"),
            rs.getString("client_net"),
            rs.getString("trace_id"),
            (Map<String, Object>) rs.getObject("detail")
    );

    public List<AuditEventView> findByCriteria(String from, Instant to, String actor,
                                               List<String> actions, List<String> outcomes,
                                               int page, int size) {
        StringBuilder sql = new StringBuilder(
                "SELECT e.id, e.occurred_at, e.actor, e.actor_type, e.action, e.target_type, e.target_id, "
                        + "e.outcome, e.source, e.client_net, e.trace_id, e.detail, "
                        + "COUNT(*) OVER () AS total "
                        + "FROM audit_event e WHERE e.occurred_at >= ?");
        var params = new java.util.ArrayList<Object>();
        params.add(java.sql.Timestamp.from(Instant.parse(from)));

        if (to != null) {
            sql.append(" AND e.occurred_at <= ?");
            params.add(java.sql.Timestamp.from(to));
        }
        if (actor != null && !actor.isBlank()) {
            sql.append(" AND e.actor = ?");
            params.add(actor);
        }
        if (actions != null && !actions.isEmpty()) {
            sql.append(" AND e.action = ANY(?)");
            params.add(actions.toArray(new String[0]));
        }
        if (outcomes != null && !outcomes.isEmpty()) {
            sql.append(" AND e.outcome = ANY(?)");
            params.add(outcomes.toArray(new String[0]));
        }

        sql.append(" ORDER BY e.occurred_at DESC, e.id");
        sql.append(" LIMIT ? OFFSET ?");
        params.add(size);
        params.add((long) page * size);

        return jdbcTemplate.query(sql.toString(), ROW_MAPPER, params.toArray());
    }

    public int countByCriteria(String from, Instant to, String actor,
                               List<String> actions, List<String> outcomes) {
        StringBuilder sql = new StringBuilder(
                "SELECT COUNT(*) FROM audit_event e WHERE e.occurred_at >= ?");
        var params = new java.util.ArrayList<Object>();
        params.add(java.sql.Timestamp.from(Instant.parse(from)));

        if (to != null) {
            sql.append(" AND e.occurred_at <= ?");
            params.add(java.sql.Timestamp.from(to));
        }
        if (actor != null && !actor.isBlank()) {
            sql.append(" AND e.actor = ?");
            params.add(actor);
        }
        if (actions != null && !actions.isEmpty()) {
            sql.append(" AND e.action = ANY(?)");
            params.add(actions.toArray(new String[0]));
        }
        if (outcomes != null && !outcomes.isEmpty()) {
            sql.append(" AND e.outcome = ANY(?)");
            params.add(outcomes.toArray(new String[0]));
        }

        return jdbcTemplate.queryForObject(sql.toString(), Integer.class, params.toArray());
    }

    public int countForExportLimit(String from, Instant to, String actor,
                                   List<String> actions, List<String> outcomes) {
        StringBuilder sql = new StringBuilder(
                "SELECT COUNT(*) FROM (SELECT 1 FROM audit_event e WHERE e.occurred_at >= ?");
        var params = new java.util.ArrayList<Object>();
        params.add(java.sql.Timestamp.from(Instant.parse(from)));

        if (to != null) {
            sql.append(" AND e.occurred_at <= ?");
            params.add(java.sql.Timestamp.from(to));
        }
        if (actor != null && !actor.isBlank()) {
            sql.append(" AND e.actor = ?");
            params.add(actor);
        }
        if (actions != null && !actions.isEmpty()) {
            sql.append(" AND e.action = ANY(?)");
            params.add(actions.toArray(new String[0]));
        }
        if (outcomes != null && !outcomes.isEmpty()) {
            sql.append(" AND e.outcome = ANY(?)");
            params.add(outcomes.toArray(new String[0]));
        }
        sql.append(" LIMIT 50001) t");

        return jdbcTemplate.queryForObject(sql.toString(), Integer.class, params.toArray());
    }
}