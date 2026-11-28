package com.triagedeck.alert.api;

import java.time.Instant;

/**
 * 清除信息；未清除时为 null。
 */
public record ClearedInfo(
        Instant clearedAt,
        String clearedBy,
        String clearedReason, // nullable
        String clearMode // MANUAL | SYSTEM
) {
}