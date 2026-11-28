package com.triagedeck.ai.mapper;

import com.triagedeck.alert.domain.Severity;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QueryFilterMapperTest {

    @Test
    void should_not_report_scope_active_as_dropped() {
        QueryFilterMapper.Result<?> result =
                QueryFilterMapper.map("{\"scope\":\"active\",\"keyword\":\"支付\"}");
        assertFalse(result.isParseError);
        assertFalse(result.droppedFields.contains("scope"));
    }

    @Test
    void should_report_scope_history_as_dropped() {
        QueryFilterMapper.Result<?> result =
                QueryFilterMapper.map("{\"scope\":\"history\",\"keyword\":\"支付\"}");
        assertTrue(result.droppedFields.contains("scope"));
    }

    @Test
    void should_parse_offset_datetime_as_instant() {
        var result = QueryFilterMapper.map(
                "{\"severities\":[\"CRITICAL\"],\"from\":\"2026-11-27T00:00:00+08:00\"}");
        assertFalse(result.isParseError);
        assertEquals(Severity.CRITICAL, result.value.severity());
        assertEquals(Instant.parse("2026-11-26T16:00:00Z"), result.value.occurredAtFrom());
    }

    @Test
    void should_reject_disallowed_key() {
        assertTrue(QueryFilterMapper.map("{\"sql\":\"drop table\"}").isParseError);
    }
}