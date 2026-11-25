package io.github.wangkui.triagedeck.ai.mapper;

import io.github.wangkui.triagedeck.alert.api.AlertFilter;
import java.util.*;

/**
 * Maps AI model JSON to AlertFilter with whitelist validation.
 * - Only 7 whitelisted keys allowed (per 02b-contract.md §3 #9)
 * - Non-whitelisted keys → droppedFields + 422 AI_UNPARSEABLE
 * - Disallowed keys (sql, orderBy) → immediate 422
 */
public class QueryFilterMapper {

    private static final Set<String> WHITELISTED = Set.of("keyword", "severity", "source", "from", "to", "resourceId", "type");
    private static final Set<String> DISALLOWED = Set.of("sql", "orderBy", "limit", "offset", "page", "sort");

    public static Result<AlertFilter> map(String json) {
        if (json == null || json.trim().isEmpty()) return error(Collections.emptyList());

        Map<String, Object> map;
        try { map = parse(json); }
        catch (Exception e) { return error(Collections.emptyList()); }

        List<String> dropped = new ArrayList<>();
        boolean hasWl = false;

        // Check disallowed keys first (security)
        for (String key : map.keySet()) {
            String lower = key.toLowerCase().trim();
            if (DISALLOWED.contains(lower)) return error(new ArrayList<>(Collections.singletonList(key)));
            if (!WHITELISTED.contains(lower)) { dropped.add(key); }
            else { hasWl = true; }
        }

        if (!hasWl) return error(dropped);

        AlertFilter.Builder b = AlertFilter.newBuilder();
        for (Map.Entry<String, Object> e : map.entrySet()) {
            String lower = e.getKey().toLowerCase().trim();
            if (WHITELISTED.contains(lower)) {
                Object v = e.getValue();
                switch (lower) {
                    case "keyword" -> b.keyword(v instanceof String ? (String) v : "");
                    case "severity" -> {
                        if (v instanceof String) {
                            try { b.severity(AlertFilter.Severity.valueOf(((String) v).toUpperCase())); } catch (Exception ignored) {}
                        }
                    }
                    case "source" -> b.source(v instanceof String ? (String) v : "")
                    case "resourceId" -> b.resourceId(v instanceof String ? (String) v : "")
                    case "type" -> b.type(v instanceof String ? (String) v : "")
                    case "from" -> { /* Instant handled elsewhere */ }
                    case "to" -> { /* Instant handled elsewhere */ }
                }
            }
        }

        AlertFilter f = b.build();
        if (f.getKeyword() == null) f = new AlertFilter(f.getSeverity(), f.getSource(), "", f.getOccurredAtFrom(), f.getOccurredAtTo());
        if (f.getSource() == null || f.getSource().isEmpty()) f = new AlertFilter(f.getSeverity(), "", f.getKeyword(), f.getOccurredAtFrom(), f.getOccurredAtTo());
        return new Result<>(f, dropped, false);
    }

    private static Result<AlertFilter> error(List<String> dropped) { return new Result<>(null, dropped, true); }

    private static Map<String, Object> parse(String json) {
        Map<String, Object> r = new HashMap<>();
        String inner = json.trim();
        if (inner.startsWith("{") && inner.endsWith("}")) inner = inner.substring(1, inner.length() - 1);
        for (String p : inner.split(",")) {
            String[] kv = p.split(":", 2);
            if (kv.length == 2) {
                String k = kk[0].trim().replace("\"", "").replace("'", "");
                String v = kk[1].trim();
                if (v.startsWith("\"") && v.endsWith("\"")) r.put(k, v.substring(1, v.length() - 1));
                else if (v.startsWith("'") && v.endsWith("'")) r.put(k, v.substring(1, v.length() - 1));
                else { try { r.put(k, Integer.parseInt(v)); } catch (Exception) { try { r.put(k, Double.parseDouble(v)); } catch (Exception) { r.put(k, v); } } }
            }
        }
        return r;
    }

    public static class Result<T> {
        public final T value; public final List<String> droppedFields; public final boolean isParseError;
        public Result(T v, List<String> df, boolean pe) { value = v; droppedFields = df; isParseError = pe; }
    }
}