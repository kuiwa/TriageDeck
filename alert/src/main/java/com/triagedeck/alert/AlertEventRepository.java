package com.triagedeck.alert.repo;

import com.triagedeck.alert.api.AlertEventView;
import com.triagedeck.alert.domain.Severity;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public class AlertEventRepository {

    private static final int MAX_EVENTS = 20;

    private static final String FIND_EVENTS_SQL =
            "SELECT id, occurred_at, severity, message, ingress "
                    + "FROM alert_event "
                    + "WHERE alert_id = :alertId "
                    + "ORDER BY occurred_at DESC, id DESC "
                    + "LIMIT :limit";

    private final NamedParameterJdbcTemplate jdbc;

    public AlertEventRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<AlertEventView> findRecentByAlertId(UUID alertId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("alertId", alertId)
                .addValue("limit", MAX_EVENTS);
        return jdbc.query(FIND_EVENTS_SQL, params, (rs, rowNum) -> new AlertEventView(
                UUID.fromString(rs.getString("id")),
                rs.getTimestamp("occurred_at").toInstant(),
                Severity.valueOf(rs.getString("severity")),
                rs.getString("message"),
                rs.getString("ingress")
        ));
    }
}