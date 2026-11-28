package com.triagedeck.audit.api;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.format.DateTimeParseException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuditControllerTimeParseTest {

    @Test
    void should_parse_full_instant_with_z() {
        assertEquals(
                Instant.parse("2026-11-21T05:54:00Z"),
                AuditController.parseInstantTolerant("2026-11-21T05:54:00Z"));
    }

    @Test
    void should_parse_datetime_local_as_utc() {
        assertEquals(
                Instant.parse("2026-11-21T05:54:00Z"),
                AuditController.parseInstantTolerant("2026-11-21T05:54"));
    }

    @Test
    void should_parse_offset_datetime() {
        assertEquals(
                Instant.parse("2026-11-20T21:54:00Z"),
                AuditController.parseInstantTolerant("2026-11-21T05:54:00+08:00"));
    }

    @Test
    void should_throw_on_garbage() {
        assertThrows(DateTimeParseException.class,
                () -> AuditController.parseInstantTolerant("not-a-time"));
    }
}