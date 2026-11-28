package com.triagedeck.alert.api;

import java.util.List;

/**
 * 已清除告警的分页视图（#5 GET /api/alerts/history）。
 */
public record AlertHistoryPage(
        List<AlertHistoryView> items,
        long total,
        int page,
        int size) {
}
