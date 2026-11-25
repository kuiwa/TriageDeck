package io.github.wangkui.triagedeck.alert.repo;

import io.github.wangkui.triagedeck.alert.api.AlertView;
import io.github.wangkui.triagedeck.alert.domain.Severity;
import java.time.LocalDateTime;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

@Repository
public class AlertActiveRepository {

    private static final String FIND_ACTIVE_SQL =
            "SELECT id, severity, source, type, resource_id, event_code, message, "
            + "first_seen_at, last_seen_at, occurrence_count, fingerprint "
            + "FROM alerts_active ";

    private final NamedParameterJdbcTemplate jdbc;

    public AlertActiveRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Page<AlertView> findActive(
            SqlParameterSource params,
            int page,
            int size) {

        MapSqlParameterSource searchParams = new MapSqlParameterSource(params);

        // Build WHERE clause conditions
        StringBuilder where = new StringBuilder("WHERE 1=1 ");
        if (searchParams.getString("severity") != null) {
            where.append("AND severity = :severity ");
        }
        if (searchParams.getString("source") != null) {
            where.append("AND source = :source ");
        }
        if (searchParams.getString("from") != null) {
            // from/to act on lastSeenAt
            LocalDateTime fromLDT = LocalDateTime.parse(searchParams.getString("from"));
            where.append("AND last_seen_at >= :from_dt ");
            searchParams = searchParams.addValue("from_dt", fromLDT);
        }
        if (searchParams.getString("to") != null) {
            LocalDateTime toLDT = LocalDateTime.parse(searchParams.getString("to"));
            where.append("AND last_seen_at <= :to_dt ");
            searchParams = searchParams.addValue("to_dt", toLDT);
        }
        if (searchParams.getString("keyword") != null) {
            where.append("AND message LIKE :keyword ");
            searchParams = searchParams.addValue("keyword", "%" + searchParams.getString("keyword") + "%");
        }

        // Count query for pagination
        String countSql = "SELECT COUNT(*) FROM alerts_active " + where + "GROUP BY id";
        long total = jdbc.queryForObject(countSql, searchParams, Long.class);

        // Data query with pagination
        int offset = page * size;
        String dataSql = FIND_ACTIVE_SQL + where + "ORDER BY severity_rank DESC, last_seen_at DESC, id DESC "
                + "LIMIT :size OFFSET :offset";

        SqlParameterSource dataParams = new MapSqlParameterSource(searchParams)
                .addValue("size", size)
                .addValue("offset", offset);

        List<AlertView> items = jdbc.query(dataSql, dataParams, (rs, rowNum) -> {
            AlertView.AlertViewBuilder builder = AlertView.builder();
            builder.id(UUID.fromString(rs.getString("id")));
            builder.severity(Severity.valueOf(rs.getString("severity")));
            builder.source(rs.getString("source"));
            builder.type(rs.getString("type"));
            builder.resourceId(rs.getString("resource_id"));
            builder.eventCode(rs.getString("event_code"));
            builder.message(rs.getString("message"));
            builder.firstSeenAt(rs.getTimestamp("first_seen_at").toInstant());
            builder.lastSeenAt(rs.getTimestamp("last_seen_at").toInstant());
            builder.occurrenceCount(rs.getLong("occurrence_count"));
            builder.fingerprint(rs.getString("fingerprint"));
            return builder.build();
        });

        return new Page<>(items, total);
    }

    public static class Page<T> {
        private final List<T> items;
        private final long total;
        private final int page;
        private final int size;

        public Page(List<T> items, long total) {
            this.items = items;
            this.total = total;
            this.page = 0;
            this.size = items.isEmpty() ? 0 : items.size();
        }

        public List<T> getItems() { return items; }
        public long getTotal() { return total; }
        public int getPage() { return page; }
        public int getSize() { return size; }
        public boolean isEmpty() { return items.isEmpty(); }
    }
}