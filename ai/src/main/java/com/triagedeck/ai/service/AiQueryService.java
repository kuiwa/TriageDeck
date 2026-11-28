package com.triagedeck.ai.service;

import com.triagedeck.ai.AiGateway;
import com.triagedeck.ai.api.AiFilter;
import com.triagedeck.ai.api.AiQueryItem;
import com.triagedeck.ai.api.AiQueryRequest;
import com.triagedeck.ai.api.AiQueryResponse;
import com.triagedeck.ai.mapper.QueryFilterMapper;
import com.triagedeck.alert.api.AlertFilter;
import com.triagedeck.alert.api.AlertPageView;
import com.triagedeck.alert.api.AlertView;
import com.triagedeck.alert.api.port.AlertReadPort;
import com.triagedeck.platform.trace.TraceIds;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * AI 自然语言查询服务。
 * - 将自然语言 question 解析为受控 filter（当前为模拟解析，未接入真实模型）；
 * - 复用 {@link QueryFilterMapper} 做白名单校验，非法键 → 422 {@code AI_UNPARSEABLE}；
 * - 仅读 {@link AlertReadPort}，不调用任何写服务（符合 AGENTS §2.1.8）。
 */
@Service
public class AiQueryService {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 50;

    private final AlertReadPort alertReadPort;
    private final TraceIds traceIds;
    private final AiGateway aiGateway;

    public AiQueryService(AlertReadPort alertReadPort,
                          TraceIds traceIds,
                          AiGateway aiGateway) {
        this.alertReadPort = alertReadPort;
        this.traceIds = traceIds;
        this.aiGateway = aiGateway;
    }

    @Transactional(readOnly = true)
    public AiQueryResponse query(AiQueryRequest request) {
        String question = request.question();
        int limit = clampLimit(request.limit());

        if (!aiGateway.isEnabled()) {
            throw new AiQueryUnavailableException(traceIds.current());
        }

        Optional<String> modelJson = aiGateway.toFilterJson(question);
        boolean degraded = modelJson.isEmpty();
        String filterJson = modelJson.orElseGet(() -> toJson(parseQuestionToFilter(question)));

        QueryFilterMapper.Result<AlertFilter> result = QueryFilterMapper.map(filterJson);
        if (result.isParseError || result.value == null) {
            throw new AiQueryParseException(result.droppedFields, traceIds.current());
        }

        AlertFilter filter = result.value;
        AlertPageView page = alertReadPort.findAll(filter, 0, limit);

        List<String> severities = new ArrayList<>();
        if (filter.severity() != null) severities.add(filter.severity().name());
        List<String> sources = filter.source() != null ? List.of(filter.source()) : List.of();

        AiFilter aiFilter = new AiFilter(
                "active",
                filter.occurredAtFrom() != null ? filter.occurredAtFrom().toString() : null,
                filter.occurredAtTo() != null ? filter.occurredAtTo().toString() : null,
                severities, sources, filter.keyword(), null
        );

        List<AiQueryItem> items = new ArrayList<>();
        for (AlertView alert : page.items()) {
            items.add(AiQueryItem.from(alert));
        }

        String explanation = degraded
                ? "大模型未配置或调用失败，已回退关键词规则解析（结果可能不完整）。"
                : "已由大模型解析为受控筛选条件（" + aiGateway.modelName() + "）。";

        return new AiQueryResponse(
                aiFilter, items, page.total(), page.page(), page.size(),
                explanation, result.droppedFields
        );
    }

    private int clampLimit(Integer limit) {
        if (limit == null) return DEFAULT_LIMIT;
        return Math.max(1, Math.min(limit, MAX_LIMIT));
    }

    private Map<String, Object> parseQuestionToFilter(String question) {
        Map<String, Object> map = new java.util.LinkedHashMap<>();
        String q = question.toLowerCase(Locale.ROOT);

        List<String> severities = new ArrayList<>();
        if (containsAny(q, new String[]{"critical", "致命", "严重"})) severities.add("CRITICAL");
        else if (containsAny(q, new String[]{"high", "高"})) severities.add("HIGH");
        else if (containsAny(q, new String[]{"medium", "中"})) severities.add("MEDIUM");
        else if (containsAny(q, new String[]{"low", "低"})) severities.add("LOW");
        else if (containsAny(q, new String[]{"info", "提示"})) severities.add("INFO");
        if (!severities.isEmpty()) map.put("severities", severities);

        String[] bizWords = new String[]{"支付", "订单", "登录", "数据库", "网络", "超时", "失败", "异常"};
        for (String w : bizWords) {
            if (question.contains(w)) {
                map.put("keyword", w);
                break;
            }
        }

        if (containsAny(q, new String[]{"昨天"})) {
            Instant yesterday = Instant.now()
                    .minus(1, ChronoUnit.DAYS)
                    .atZone(ZoneOffset.UTC).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant();
            map.put("from", yesterday.toString());
            map.put("to", yesterday.plus(1, ChronoUnit.DAYS).minus(1, ChronoUnit.MILLIS).toString());
        } else if (containsAny(q, new String[]{"今天"})) {
            Instant today = Instant.now()
                    .atZone(ZoneOffset.UTC).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant();
            map.put("from", today.toString());
            map.put("to", today.plus(1, ChronoUnit.DAYS).minus(1, ChronoUnit.MILLIS).toString());
        } else if (containsAny(q, new String[]{"最近7天", "近七天", "7天"})) {
            map.put("from", Instant.now().minus(7, ChronoUnit.DAYS).toString());
        }

        return map;
    }

    private boolean containsAny(String q, String[] words) {
        for (String w : words) {
            if (q.contains(w.toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

    private String toJson(Map<String, Object> map) {
        if (map.isEmpty()) return "{}";
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> e : map.entrySet()) {
            if (!first) sb.append(",");
            first = false;
            sb.append("\"").append(e.getKey()).append("\":");
            Object v = e.getValue();
            if (v instanceof List<?> list) {
                sb.append("[");
                for (int i = 0; i < list.size(); i++) {
                    if (i > 0) sb.append(",");
                    sb.append("\"").append(list.get(i)).append("\"");
                }
                sb.append("]");
            } else {
                sb.append("\"").append(v).append("\"");
            }
        }
        sb.append("}");
        return sb.toString();
    }

    public static class AiQueryParseException extends RuntimeException {
        public final List<String> droppedFields;
        public AiQueryParseException(List<String> droppedFields, String traceId) {
            super("AI_UNPARSEABLE: " + traceId);
            this.droppedFields = droppedFields != null ? droppedFields : List.of();
        }
    }

    public static class AiQueryUnavailableException extends RuntimeException {
        public AiQueryUnavailableException(String traceId) {
            super("AI_UNAVAILABLE: " + traceId);
        }
    }

}

