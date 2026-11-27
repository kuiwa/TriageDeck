package com.triagedeck.alert.api;

import com.triagedeck.alert.domain.Severity;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 告警详情视图（对应 openapi {@code AlertDetailView}）。
 * 活动/历史通用；含 mergeFields/mergeRule/latestPayload/events/cleared。
 */
public record AlertDetailView(
        UUID id,
        String status, // ACTIVE | CLEARED
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
        MergeFields mergeFields,
        String mergeRule,
        Object latestPayload,
        List<AlertEventView> events,
        ClearedInfo cleared // null when still active
) {
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private UUID id;
        private String status;
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
        private MergeFields mergeFields;
        private String mergeRule;
        private Object latestPayload;
        private List<AlertEventView> events;
        private ClearedInfo cleared;

        private Builder() {
        }

        public Builder id(UUID id) {
            this.id = id;
            return this;
        }

        public Builder status(String status) {
            this.status = status;
            return this;
        }

        public Builder severity(Severity severity) {
            this.severity = severity;
            return this;
        }

        public Builder source(String source) {
            this.source = source;
            return this;
        }

        public Builder type(String type) {
            this.type = type;
            return this;
        }

        public Builder resourceId(String resourceId) {
            this.resourceId = resourceId;
            return this;
        }

        public Builder eventCode(String eventCode) {
            this.eventCode = eventCode;
            return this;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder firstSeenAt(Instant firstSeenAt) {
            this.firstSeenAt = firstSeenAt;
            return this;
        }

        public Builder lastSeenAt(Instant lastSeenAt) {
            this.lastSeenAt = lastSeenAt;
            return this;
        }

        public Builder occurrenceCount(long occurrenceCount) {
            this.occurrenceCount = occurrenceCount;
            return this;
        }

        public Builder fingerprint(String fingerprint) {
            this.fingerprint = fingerprint;
            return this;
        }

        public Builder mergeFields(MergeFields mergeFields) {
            this.mergeFields = mergeFields;
            return this;
        }

        public Builder mergeRule(String mergeRule) {
            this.mergeRule = mergeRule;
            return this;
        }

        public Builder latestPayload(Object latestPayload) {
            this.latestPayload = latestPayload;
            return this;
        }

        public Builder events(List<AlertEventView> events) {
            this.events = events;
            return this;
        }

        public Builder cleared(ClearedInfo cleared) {
            this.cleared = cleared;
            return this;
        }

        public AlertDetailView build() {
            return new AlertDetailView(
                    id, status, severity, source, type, resourceId, eventCode, message,
                    firstSeenAt, lastSeenAt, occurrenceCount, fingerprint,
                    mergeFields, mergeRule, latestPayload, events, cleared);
        }
    }
}