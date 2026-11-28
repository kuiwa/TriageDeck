package com.triagedeck.audit;

import com.triagedeck.platform.audit.ActorType;
import com.triagedeck.platform.audit.AuditAction;
import com.triagedeck.platform.audit.Outcome;
import com.triagedeck.platform.audit.TargetType;
import com.triagedeck.platform.trace.TraceIds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keycloak 事件定时采集器（US-C1）：client_credentials 拉取 LOGIN/LOGOUT/LOGIN_ERROR，
 * 写入 audit_event（source_event_id 幂等），并推进 keycloak_cursor 游标。
 *
 * 修复要点：
 * - token 从「本 realm」获取，且以 Bearer 头调用 Admin API；
 * - 复用 AuditWriter 写审计（其内部用显式 REQUIRES_NEW 事务）；
 * - 游标读写用 JdbcTemplate（单语句，无需事务）；
 * - 跳过非登录类事件；用户名优先取 details.username。
 */
@Component
public class KeycloakEventCollector {

    private static final Logger log = LoggerFactory.getLogger(KeycloakEventCollector.class);
    private static final String CURSOR_ID = "realm-events";

    private final RestTemplate restTemplate;
    private final AuditWriter auditWriter;
    private final CollectorState collectorState;
    private final TraceIds traceIds;
    private final JdbcTemplate jdbcTemplate;

    /** Keycloak 26 事件不带 username（LOGOUT），按 userId 回查后缓存，避免重复调用。 */
    private final Map<String, String> usernameCache = new ConcurrentHashMap<>();

    @Value("${app.keycloak.admin.base-url:http://keycloak:8081}")
    private String keycloakBaseUrl;

    @Value("${app.keycloak.admin.realm:triagedeck}")
    private String realm;

    @Value("${app.keycloak.admin.client-id:triagedeck-audit-collector}")
    private String clientId;

    @Value("${app.keycloak.admin.client-secret:}")
    private String clientSecret;

    @Value("${app.audit.collect-page-size:100}")
    private int pageSize;

    @Value("${app.audit.collect-max-pages:5}")
    private int maxPages;

    private String cachedToken;
    private Instant tokenExpiresAt;

    public KeycloakEventCollector(RestTemplate restTemplate,
                                  AuditWriter auditWriter,
                                  CollectorState collectorState,
                                  TraceIds traceIds,
                                  JdbcTemplate jdbcTemplate) {
        this.restTemplate = restTemplate;
        this.auditWriter = auditWriter;
        this.collectorState = collectorState;
        this.traceIds = traceIds;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Scheduled(fixedDelayString = "${app.audit.collect-interval:15000}")
    public void collect() {
        String traceId = traceIds.current();
        try {
            if (clientSecret == null || clientSecret.isBlank()) {
                collectorState.setEventsDisabled(true);
                collectorState.setCollectorLastRunAt(Instant.now());
                return;
            }

            Map<String, Object> realmInfo = getRealm();
            if (realmInfo == null || !Boolean.TRUE.equals(realmInfo.get("eventsEnabled"))) {
                collectorState.setEventsDisabled(true);
                collectorState.setCollectorLastRunAt(Instant.now());
                log.info("action=keycloak_collect_skipped reason=events_disabled");
                return;
            }
            collectorState.setEventsDisabled(false);

            long lastEventTime = readCursor();
            LocalDate dateFrom = lastEventTime <= 0
                    ? LocalDate.of(1970, 1, 1)
                    : LocalDate.ofInstant(Instant.ofEpochMilli(lastEventTime), ZoneOffset.UTC)
                            .minusDays(1);

            int insertedTotal = 0;
            long maxSeen = lastEventTime;

            for (int page = 0; page < maxPages; page++) {
                String eventsUrl = keycloakBaseUrl + "/admin/realms/" + realm + "/events"
                        + "?dateFrom=" + dateFrom
                        + "&first=" + (page * pageSize)
                        + "&max=" + pageSize;
                List<Map<String, Object>> events = getEvents(eventsUrl);
                if (events == null || events.isEmpty()) {
                    break;
                }
                for (Map<String, Object> event : events) {
                    Object timeObj = event.get("time");
                    if (timeObj instanceof Number n && n.longValue() > maxSeen) {
                        maxSeen = n.longValue();
                    }
                    if (writeEvent(event, traceId)) {
                        insertedTotal++;
                    }
                }
                if (events.size() < pageSize) {
                    break;
                }
            }

            if (maxSeen > lastEventTime) {
                upsertCursor(maxSeen);
            }
            collectorState.recordRound(insertedTotal);
            collectorState.setCollectorLastRunAt(Instant.now());
            log.info("action=keycloak_collect inserted={} lastEventTime={}", insertedTotal, maxSeen);
        } catch (Exception e) {
            collectorState.setCollectorLastRunAt(Instant.now());
            log.error("action=keycloak_collect_failed reason={}", e.toString());
        }
    }

    private Map<String, Object> getRealm() {
        return restTemplate.exchange(
                keycloakBaseUrl + "/admin/realms/" + realm,
                HttpMethod.GET, new HttpEntity<>(bearerHeaders()), Map.class).getBody();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getEvents(String url) {
        ResponseEntity<List> response = restTemplate.exchange(
                url, HttpMethod.GET, new HttpEntity<>(bearerHeaders()), List.class);
        return (List<Map<String, Object>>) response.getBody();
    }

    private HttpHeaders bearerHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(getAdminToken());
        return headers;
    }

    private String getAdminToken() {
        if (cachedToken != null && tokenExpiresAt != null
                && Instant.now().isBefore(tokenExpiresAt.minusSeconds(30))) {
            return cachedToken;
        }
        String tokenUrl = keycloakBaseUrl + "/realms/" + realm
                + "/protocol/openid-connect/token";
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "client_credentials");
        body.add("client_id", clientId);
        body.add("client_secret", clientSecret);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);
        ResponseEntity<Map> response = restTemplate.postForEntity(tokenUrl, request, Map.class);
        Map<String, Object> tokenResponse = response.getBody();
        if (tokenResponse == null || tokenResponse.get("access_token") == null) {
            throw new IllegalStateException("Keycloak token response missing access_token");
        }
        cachedToken = (String) tokenResponse.get("access_token");
        Object expiresIn = tokenResponse.getOrDefault("expires_in", 300);
        tokenExpiresAt = Instant.now().plusSeconds(
                expiresIn instanceof Number n ? n.longValue() : 300L);
        return cachedToken;
    }

    private boolean writeEvent(Map<String, Object> event, String traceId) {
        String type = stringValue(event.get("type"));
        AuditAction action = mapAction(type);
        Object timeObj = event.get("time");
        if (action == null || !(timeObj instanceof Number time)) {
            return false;
        }

        String userId = stringValue(event.get("userId"));
        String ipAddress = stringValue(event.get("ipAddress"));
        String sessionId = stringValue(event.get("sessionId"));
        String sourceEventId = buildSourceEventId(type, time.longValue(), sessionId, userId, event);
        String actor = resolveActor(event, userId);

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("type", type);
        detail.put("userId", userId);
        detail.put("sessionId", sessionId);
        detail.put("ipAddress", ipAddress);
        detail.put("clientId", stringValue(event.get("clientId")));

        try {
            auditWriter.write(new AuditCommand(
                    UUID.randomUUID(),
                    Instant.ofEpochMilli(time.longValue()),
                    actor,
                    userId != null || !"unknown".equals(actor) ? ActorType.USER : ActorType.SYSTEM,
                    action,
                    TargetType.AUTH,
                    sourceEventId,
                    mapOutcome(type),
                    "KEYCLOAK",
                    sourceEventId,
                    maskIp(ipAddress),
                    traceId,
                    detail));
            return true;
        } catch (Exception e) {
            log.error("action=keycloak_insert_failed eventId={} reason={}", sourceEventId, e.toString());
            return false;
        }
    }

    /**
     * Keycloak 26 的事件表示不含 id，用 type+time+会话/用户 组合出稳定的 source_event_id，
     * 保证同一事件重复采集时被 ON CONFLICT 去重。
     */
    private String buildSourceEventId(String type, long time, String sessionId, String userId,
                                      Map<String, Object> event) {
        String discriminator = sessionId != null ? sessionId
                : userId != null ? userId
                : stringValue(event.get("ipAddress"));
        return "kc:" + type + ":" + time + ":" + (discriminator != null ? discriminator : "unknown");
    }

    private String resolveActor(Map<String, Object> event, String userId) {
        Object details = event.get("details");
        if (details instanceof Map<?, ?> map) {
            Object username = map.get("username");
            if (username instanceof String s && !s.isBlank()) {
                return s;
            }
        }
        String lookedUp = lookupUsername(userId);
        if (lookedUp != null) {
            return lookedUp;
        }
        return userId != null ? userId : "unknown";
    }

    private String lookupUsername(String userId) {
        if (userId == null) {
            return null;
        }
        String cached = usernameCache.get(userId);
        if (cached != null) {
            return cached;
        }
        try {
            Map<String, Object> user = restTemplate.exchange(
                    keycloakBaseUrl + "/admin/realms/" + realm + "/users/" + userId,
                    HttpMethod.GET, new HttpEntity<>(bearerHeaders()), Map.class).getBody();
            String username = user == null ? null : stringValue(user.get("username"));
            if (username != null) {
                usernameCache.put(userId, username);
            }
            return username;
        } catch (Exception e) {
            log.debug("action=keycloak_user_lookup_failed userId={}", userId);
            return null;
        }
    }

    private long readCursor() {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT last_event_time FROM keycloak_cursor WHERE id = ?", CURSOR_ID);
            if (rows.isEmpty()) {
                return 0L;
            }
            Object value = rows.get(0).get("last_event_time");
            return value instanceof Number n ? n.longValue() : 0L;
        } catch (Exception e) {
            return 0L;
        }
    }

    private void upsertCursor(long lastEventTime) {
        jdbcTemplate.update(
                "INSERT INTO keycloak_cursor (id, last_event_time, last_event_id, updated_at) "
                        + "VALUES (?, ?, NULL, now()) "
                        + "ON CONFLICT (id) DO UPDATE SET "
                        + "last_event_time = greatest(keycloak_cursor.last_event_time, excluded.last_event_time), "
                        + "updated_at = now()",
                CURSOR_ID, lastEventTime);
    }

    private AuditAction mapAction(String type) {
        if (type == null) {
            return null;
        }
        return switch (type) {
            case "LOGIN" -> AuditAction.LOGIN;
            case "LOGOUT" -> AuditAction.LOGOUT;
            case "LOGIN_ERROR" -> AuditAction.LOGIN_FAILED;
            default -> null;
        };
    }

    private Outcome mapOutcome(String type) {
        return "LOGIN_ERROR".equals(type) ? Outcome.FAILED : Outcome.SUCCESS;
    }

    private String maskIp(String ip) {
        if (ip == null) {
            return null;
        }
        String[] parts = ip.split("\\.");
        if (parts.length == 4) {
            return parts[0] + "." + parts[1] + "." + parts[2] + ".0/24";
        }
        return ip;
    }

    private String stringValue(Object value) {
        return value instanceof String s && !s.isBlank() ? s : null;
    }
}
