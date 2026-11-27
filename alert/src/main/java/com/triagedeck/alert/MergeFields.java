package com.triagedeck.alert.api;

/**
 * 告警合并四段键：source/type/resourceId/eventCode，用于详情页展示「为何合并成一条」。
 */
public record MergeFields(
        String source,
        String type,
        String resourceId,
        String eventCode
) {
}