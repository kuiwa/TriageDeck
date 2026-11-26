package io.github.wangkui.triagedeck.audit;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class AuditDetailSanitizer {

    private static final Set<String> BLACKLIST_KEYS = new HashSet<>(java.util.Arrays.asList(
        "token", "access_token", "refresh_token", "authorization", "password", "secret",
        "body", "payload", "requestBody", "ip", "headers", "authorization"));

    public static Map<String, Object> sanitize(Map<String, Object> detail) {
        if (detail == null) {
            return null;
        }
        java.util.Map<String, Object> result = new HashMap<>(detail);
        result.entrySet().removeIf(entry -> {
            String key = entry.getKey().toLowerCase();
            return BLACKLIST_KEYS.contains(key) || 
                   key.contains("token") || 
                   key.contains("secret") ||
                   key.contains("credential");
        });
        // Truncate values to 500 chars and remove newlines
        result.replaceAll((k, v) -> {
            if (v instanceof String) {
                String val = (String) v;
                val = val.replace("\n", " ").replace("\r", " ");
                return val.length() > 500 ? val.substring(0, 500) : val;
            }
            return v;
        });
        return result;
    }
}