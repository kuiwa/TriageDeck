package com.triagedeck.platform.event;

import java.time.Instant;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * `alert.ingested` 消息体（字段严格按 02b-contract.md §6.3，与 #5 请求体一致）。
 * DTO 放 platform：notify 不能依赖 alert（ADR-0007）。
 */
public record AlertIngestedMessage(
        String source,
        String type,
        String resourceId,
        String eventCode,
        String severity,
        String message,
        Instant occurredAt,
        JsonNode payload) {
}
