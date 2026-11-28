package com.triagedeck.ai.api;

import java.util.List;

/**
 * 简报条目（openapi {@code BriefingItem}）。
 */
public record BriefingItem(
        String text,
        double confidence,
        String priority, // HIGH | MEDIUM | LOW
        List<String> evidenceEventIds
) {
}