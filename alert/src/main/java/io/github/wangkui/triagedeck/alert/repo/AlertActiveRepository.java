package io.github.wangkui.triagedeck.alert.repo;

import io.github.wangkui.triagedeck.alert.api.AlertView;
import io.github.wangkui.triagedeck.alert.domain.Severity;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class AlertActiveRepository {

    private static final String FIND_ACTIVE_SQL =
            "SELECT id, severity, source, type, resource_id, event_code, message, "
            + "first_seen_at, last_seen_at, occurrence_count, fingerprint "
            + "FROM alerts_active ";

    private static final String FIND_BY_ID_SQL = FIND_ACTIVE_SQL + "WHERE id = :id";

    private static final String DELETE_BY_ID_SQL = "DELETE FROM alerts_active WHERE id = :id";

    private final NamedParameterJdbcTemplate jdbc;

    public AlertActiveRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Page<AlertView> findActive(MapSqlParameterSource params, int page, int size) {

        MapSqlParameterSource searchParams = new MapSqlParameterSource(params.getValues());

        // Build WHERE clause conditions
        StringBuilder where = new StringBuilder("WHERE 1=1 ");
        if (params.hasValue("severity")) {
            where.append("AND severity = :severity ");
        }
        if (params.hasValue("source")) {
            where.append("AND source = :source ");
        }
        if (params.hasValue("from")) {
            // from/to act on lastSeenAt（ISO-8601 UTC）
            OffsetDateTime from = OffsetDateTime.parse(params.getValue("from").toString());
            where.append("AND last_seen_at >= :from_dt ");
            searchParams.addValue("from_dt", from);
        }
        if (params.hasValue("to")) {
            OffsetDateTime to = OffsetDateTime.parse(params.getValue("to").toString());
            where.append("AND last_seen_at <= :to_dt ");
            searchParams.addValue("to_dt", to);
        }
        if (params.hasValue("keyword")) {
            where.append("AND message LIKE :keyword ");
            searchParams.addValue("keyword", "%" + params.getValue("keyword") + "%");
        }

        // Count query for pagination
        String countSql = "SELECT COUNT(*) FROM alerts_active " + where;
        long total = jdbc.queryForObject(countSql, searchParams, Long.class);

        // Data query with pagination
        int offset = page * size;
        String dataSql = FIND_ACTIVE_SQL + where + "ORDER BY severity_rank DESC, last_seen_at DESC, id DESC "
                + "LIMIT :size OFFSET :offset";

        searchParams.addValue("size", size);
        searchParams.addValue("offset", offset);

        List<AlertView> items = jdbc.query(dataSql, searchParams, (rs, rowNum) -> mapRow(rs));

        return new Page<>(items, total, page, size);
    }

    public Optional<AlertView> findById(UUID id) {
        List<AlertView> rows = jdbc.query(
                FIND_BY_ID_SQL, new MapSqlParameterSource("id", id), (rs, rowNum) -> mapRow(rs));
        return rows.stream().findFirst();
    }

    public int deleteById(UUID id) {
        return jdbc.update(DELETE_BY_ID_SQL, new MapSqlParameterSource("id", id));
    }

    private AlertView mapRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        return AlertView.builder()
                .id(UUID.fromString(rs.getString("id")))
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
                .build();
    }

    public static class Page<T> {
        private final List<T> items;
        private final long total;
        private final int page;
        private final int size;

        public Page(List<T> items, long total) {
            this(items, total, 0, items.isEmpty() ? 0 : items.size());
        }

        public Page(List<T> items, long total, int page, int size) {
            this.items = items;
            this.total = total;
            this.page = page;
            this.size = size;
        }

        public List<T> getItems() { return items; }
        public long getTotal() { return total; }
        public int getPage() { return page; }
        public int getSize() { return size; }
        public boolean isEmpty() { return items.isEmpty(); }
    }
}