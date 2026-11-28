package com.triagedeck.alert.api;

import com.triagedeck.alert.domain.Severity;
import java.time.Instant;
import java.util.UUID;

public record AlertHistoryView(
        UUID id,
        Severity severity,
        String source,
        String type,
        String resourceId,
        String eventCode,
        String message,
        Instant firstSeenAt,
        Instant lastSeenAt,
        long occurrenceCount,
        String fingerprint,
        Instant clearedAt,
        String clearedBy,
        String clearedReason,
        String clearMode) {

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private UUID id;
        private Severity severity;
        private String source;
        private String type;
        private String resourceId;
        private String eventCode;
        private String message;
        private Instant firstSeenAt;
        private Instant lastSeenAt;
        private long occurrenceCount;
        private String fingerprint;
        private Instant clearedAt;
        private String clearedBy;
        private String clearedReason;
        private String clearMode;

        private Builder() {}

        public Builder id(UUID id) { this.id = id; return this; }
        public Builder severity(Severity severity) { this.severity = severity; return this; }
        public Builder source(String source) { this.source = source; return this; }
        public Builder type(String type) { this.type = type; return this; }
        public Builder resourceId(String resourceId) { this.resourceId = resourceId; return this; }
        public Builder eventCode(String eventCode) { this.eventCode = eventCode; return this; }
        public Builder message(String message) { this.message = message; return this; }
        public Builder firstSeenAt(Instant firstSeenAt) { this.firstSeenAt = firstSeenAt; return this; }
        public Builder lastSeenAt(Instant lastSeenAt) { this.lastSeenAt = lastSeenAt; return this; }
        public Builder occurrenceCount(long occurrenceCount) { this.occurrenceCount = occurrenceCount; return this; }
        public Builder fingerprint(String fingerprint) { this.fingerprint = fingerprint; return this; }
        public Builder clearedAt(Instant clearedAt) { this.clearedAt = clearedAt; return this; }
        public Builder clearedBy(String clearedBy) { this.clearedBy = clearedBy; return this; }
        public Builder clearedReason(String clearedReason) { this.clearedReason = clearedReason; return this; }
        public Builder clearMode(String clearMode) { this.clearMode = clearMode; return this; }

        public AlertHistoryView build() {
            return new AlertHistoryView(
                    id, severity, source, type, resourceId, eventCode, message,
                    firstSeenAt, lastSeenAt, occurrenceCount, fingerprint,
                    clearedAt, clearedBy, clearedReason, clearMode);
        }
    }
}