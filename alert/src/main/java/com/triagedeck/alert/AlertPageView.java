package com.triagedeck.alert.api;

/**
 * Page view response for alert queries (read-only).
 * 返回空数组而非 null（AGENTS.md 约束）。
 */
public record AlertPageView(
        /** 当前页项，永远非 null（空数组而非 null） */
        java.util.List<AlertView> items,

        /** 总数 */
        long total,

        /** 当前页号 (0-indexed) */
        int page,

        /** 每页大小 */
        int size

) {
    /**
     * Create empty page view (for no-results case).
     */
    public static AlertPageView empty() {
        return new AlertPageView(java.util.Collections.emptyList(), 0, 0, 0);
    }

    /**
     * Validate that total is consistent with items size (for debugging).
     */
    public boolean isConsistent() {
        return items.size() == 0 ? total == 0 : true;
    }
}