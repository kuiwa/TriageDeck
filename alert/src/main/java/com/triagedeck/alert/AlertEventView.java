package com.triagedeck.alert.api;

import com.triagedeck.alert.domain.Severity;
import java.time.Instant;
import java.util.UUID;

/**
 * 单条告警事件（证据链），用于详情页事件列表（最新 20 条）。
 */
public record AlertEventView(
        UUID id,
        Instant occurredAt,
        Severity severity,
        String message,
        String ingress // HTTP | KAFKA
) {
}