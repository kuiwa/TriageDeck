package com.triagedeck.ai.api;

import java.util.List;

/**
 * 结构化告警简报（openapi {@code Briefing}）。
 * summary 仅在 degraded=true 时出现。
 */
public record Briefing(
        List<BriefingItem> impact,
        List<BriefingItem> hypotheses,
        List<BriefingItem> actions,
        String summary
) {
}