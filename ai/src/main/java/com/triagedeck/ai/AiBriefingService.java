package com.triagedeck.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.triagedeck.ai.api.Briefing;
import com.triagedeck.ai.api.BriefingItem;
import com.triagedeck.alert.api.AlertView;
import com.triagedeck.alert.api.port.AlertReadPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AiBriefingService {

    private static final long TIMEOUT_MS = 120_000L;

    private final AlertReadPort alertReadPort;
    private final ObjectMapper objectMapper;

    public AiBriefingService(AlertReadPort alertReadPort, ObjectMapper objectMapper) {
        this.alertReadPort = alertReadPort;
        this.objectMapper = objectMapper;
    }

    /**
     * 告警简报 SSE。事件遵循 openapi #8：
     * delta {"text":".."} 逐段输出；done {"briefing":..,"degraded":..,"model":..,"promptVersion":..}。
     * 仅读告警，不调用任何写服务。
     */
    @Transactional(readOnly = true)
    public SseEmitter briefingSse(UUID alertId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);

        AlertView alert = alertReadPort.findById(alertId);
        if (alert == null) {
            emitter.completeWithError(new IllegalArgumentException("Alert not found: " + alertId));
            return emitter;
        }

        try {
            Briefing briefing = buildBriefing(alert);
            for (String chunk : splitSummary(briefing.summary())) {
                emitter.send(SseEmitter.event()
                        .name("delta")
                        .data(objectMapper.writeValueAsString(Map.of("text", chunk))));
            }
            emitter.send(SseEmitter.event()
                    .name("done")
                    .data(objectMapper.writeValueAsString(Map.of(
                            "briefing", briefing,
                            "degraded", true,
                            "model", "mock",
                            "promptVersion", "briefing-v1"))));
            emitter.complete();
        } catch (Exception e) {
            emitter.completeWithError(e);
        }
        return emitter;
    }

    private Briefing buildBriefing(AlertView alert) {
        String impactText = String.format(
                "级别 %s，来源 %s，资源 %s，最近发生 %s，累计 %d 次。",
                alert.severity(), alert.source(), alert.resourceId(),
                alert.lastSeenAt(), alert.occurrenceCount());
        String hypothesisText = String.format(
                "可能原因：%s。需结合日志与指标进一步确认。", alert.message());
        String actionText = String.format(
                "检查 %s/%s 的监控与日志；按 SOP 处置，确认恢复后可清除告警。",
                alert.source(), alert.resourceId());

        BriefingItem impact = new BriefingItem(impactText, 0.6, "HIGH", List.of());
        BriefingItem hypothesis = new BriefingItem(hypothesisText, 0.4, "MEDIUM", List.of());
        BriefingItem action = new BriefingItem(actionText, 0.5, "MEDIUM", List.of());
        String summary = impactText + " 建议：" + actionText;
        return new Briefing(List.of(impact), List.of(hypothesis), List.of(action), summary);
    }

    private List<String> splitSummary(String summary) {
        List<String> chunks = new ArrayList<>();
        int step = 24;
        for (int i = 0; i < summary.length(); i += step) {
            chunks.add(summary.substring(i, Math.min(summary.length(), i + step)));
        }
        return chunks;
    }
}