package io.github.wangkui.triagedeck.alert.repo;

import java.sql.Timestamp;
import java.util.UUID;

import io.github.wangkui.triagedeck.alert.domain.AlertIngestCommand;
import io.github.wangkui.triagedeck.alert.domain.AlertIngestResult;
import io.github.wangkui.triagedeck.alert.domain.Fingerprint;
import io.github.wangkui.triagedeck.alert.domain.Ingress;
import io.github.wangkui.triagedeck.alert.domain.Severity;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AlertIngestRepository {

    private static final String UPSERT_SQL = """
            with upsert as (
              insert into alerts_active (
                id, fingerprint, source, type, resource_id, event_code, severity,
                message, first_seen_at, last_seen_at, occurrence_count,
                latest_payload, latest_event_id, created_at, updated_at
              ) values (
                :alertId, :fingerprint, :source, :type, :resourceId, :eventCode, :severity,
                :message, :occurredAt, :occurredAt, 1,
                cast(:payload as jsonb), :eventId, now(), now()
              )
              on conflict (fingerprint) do update set
                occurrence_count = alerts_active.occurrence_count + 1,
                last_seen_at = greatest(alerts_active.last_seen_at, excluded.last_seen_at),
                severity = case
                  when excluded.severity_rank > alerts_active.severity_rank then excluded.severity
                  else alerts_active.severity
                end,
                message = excluded.message,
                latest_payload = excluded.latest_payload,
                latest_event_id = excluded.latest_event_id,
                updated_at = now()
              returning id, occurrence_count, severity, last_seen_at, (xmax = 0) as created
            )
            select id, occurrence_count, severity, last_seen_at, created
            from upsert
            """;

    private static final String INSERT_EVENT_SQL = """
            insert into alert_event (
              id, alert_id, occurred_at, severity, message, payload, ingress
            ) values (
              :eventId, :alertId, :occurredAt, :severity, :message,
              cast(:payload as jsonb), :ingress
            )
            """;

    private static final String TRIM_EVENTS_SQL = """
            delete from alert_event
            where alert_id = :alertId
              and id not in (
                select id
                from alert_event
                where alert_id = :alertId
                order by occurred_at desc, id desc
                limit 50
              )
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public AlertIngestRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public AlertIngestResult upsertAndRecord(
            AlertIngestCommand command,
            Fingerprint fingerprint,
            Ingress ingress,
            UUID alertId,
            UUID eventId,
            String payloadJson) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("alertId", alertId)
                .addValue("eventId", eventId)
                .addValue("fingerprint", fingerprint.value())
                .addValue("source", command.source())
                .addValue("type", command.type())
                .addValue("resourceId", command.resourceId())
                .addValue("eventCode", command.eventCode())
                .addValue("severity", command.severity().name())
                .addValue("message", command.message())
                .addValue("occurredAt", Timestamp.from(command.occurredAt()))
                .addValue("payload", payloadJson);

        AlertIngestResult result = jdbc.queryForObject(UPSERT_SQL, parameters, (rs, rowNum) ->
                new AlertIngestResult(
                        rs.getObject("id", UUID.class),
                        rs.getBoolean("created"),
                        rs.getInt("occurrence_count"),
                        Severity.valueOf(rs.getString("severity")),
                        rs.getTimestamp("last_seen_at").toInstant()));

        parameters.addValue("ingress", ingress.name());
        parameters.addValue("alertId", result.id());
        jdbc.update(INSERT_EVENT_SQL, parameters);
        jdbc.update(TRIM_EVENTS_SQL, new MapSqlParameterSource("alertId", result.id()));
        return result;
    }
}
