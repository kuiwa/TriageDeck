package io.github.wangkui.triagedeck.audit.keyword;

import io.github.wangkui.triagedeck.audit.AuditCommand;
import io.github.wangkui.triagedeck.audit.AuditWriter;
import io.github.wangkui.triagedeck.audit.CollectorState;
import io.github.wangkui.triagedeck.platform.audit.ActorType;
import io.github.wangkui.triagedeck.platform.audit.AuditAction;
import io.github.wangkui.triagedeck.platform.audit.Outcome;
import io.github.wangkui.triagedeck.platform.audit.TargetType;
import io.github.wangkui.triagedeck.platform.trace.TraceIds;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.HttpEntity;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Keycloak 事件定时采集器（US-C1）。
 * 每 15s 触发一次，通过 client_credentials 调用 Keycloak Admin API 拉取登录/登出/登录失败事件，
 * 批量写入 audit_event（幂等：on conflict (source_event_id) do nothing），推进 keycloak_cursor 游标。
 */
@Component
public class KeycloakEventCollector {

    private static final String REALM_EVENTS_ID = "realm-events";

    private final RestTemplate restTemplate;
    private final AuditWriter auditWriter;
    private final CollectorState collectorState;
    private final TraceIds traceIds;

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

    private String cachedToken = null;
    private Instant tokenExpiresAt = null;

    public KeycloakEventCollector(RestTemplate restTemplate,
                                  AuditWriter auditWriter,
                                  CollectorState collectorState,
                                  TraceIds traceIds) {
        this.restTemplate = restTemplate;
        this.auditWriter = auditWriter;
        this.collectorState = collectorState;
        this.traceIds = traceIds;
    }

    @Scheduled(fixedDelayString = "${app.audit.collect-interval:15000}")
    @Transactional
    public void collect() {
        String traceId = traceIds.current();
        try {
            if (clientSecret == null || clientSecret.isBlank()) {
                collectorState.setEventsDisabled(true);
                collectorState.setCollectorLastRunAt(Instant.now());
                return;
            }

            String eventsEnabledUrl = keycloakBaseUrl + "/admin/realms/" + realm;
            ResponseEntity<Map> realmResp = restTemplate.getForEntity(
                    eventsEnabledUrl, Map.class, new Object[]{getAdminToken()});
            if (!Boolean.TRUE.equals(realmResp.getBody().get("eventsEnabled"))) {
                collectorState.setEventsDisabled(true);
                collectorState.setCollectorLastRunAt(Instant.now());
                return;
            }
            collectorState.setEventsDisabled(false);

            Map<String, Object> cursor = readCursor();
            long lastEventTime = ((Number) cursor.get("lastEventTime")).longValue();
            String lastEventId = (String) cursor.get("lastEventId");

            int totalNew = 0;
            String dateFrom = Instant.ofEpochMilli(lastEventTime).toString();

            for (int page = 0; page < maxPages; page++) {
                String eventsUrl = keycloakBaseUrl + "/admin/realms/" + realm + "/events"
                        + "?dateFrom=" + dateFrom
                        + "&first=" + (page * pageSize)
                        + "&max=" + pageSize;
                ResponseEntity<List> eventsResp = restTemplate.getForEntity(
                        eventsUrl, List.class, new Object[]{getAdminToken()});
                List<Map<String, Object>> events = eventsResp.getBody();
                if (events == null || events.isEmpty()) {
                    break;
                }

                int inserted = batchInsert(events, traceId);
                totalNew += inserted;

                if (inserted < pageSize) {
                    break;
                }

                Map<String, Object> last = events.get(events.size() - 1);
                lastEventTime = ((Number) last.get("time")).longValue();
                lastEventId = (String) last.get("id");
            }

            upsertCursor(lastEventTime, lastEventId);
            collectorState.recordRound(totalNew);
            collectorState.setCollectorLastRunAt(Instant.now());

        } catch (Exception e) {
            collectorState.setCollectorLastRunAt(Instant.now());
            System.err.println("keycloak_collect_error traceId=" + traceId + " msg=" + e.getMessage());
        }
    }

    private String getAdminToken() {
        if (cachedToken != null && tokenExpiresAt != null && Instant.now().isBefore(tokenExpiresAt.minusSeconds(30))) {
            return cachedToken;
        }
        String tokenUrl = keycloakBaseUrl + "/realms/master/protocol/openid-connect/token";
        Map<String, String> body = new LinkedHashMap<>();
        body.put("grant_type", "client_credentials");
        body.put("client_id", clientId);
        body.put("client_secret", clientSecret);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);
        ResponseEntity<Map> resp = restTemplate.postForEntity(tokenUrl, request, Map.class);
        Map<String, Object> tokenResp = resp.getBody();
        cachedToken = (String) tokenResp.get("access_token");
        int expiresIn = (Integer) tokenResp.getOrDefault("expires_in", 300);
        tokenExpiresAt = Instant.now().plusSeconds(expiresIn);
        return cachedToken;
    }

    @PersistenceContext
    private EntityManager entityManager;

    private int batchInsert(List<Map<String, Object>> events, String traceId) {
        int inserted = 0;
        for (Map<String, Object> event : events) {
            String eventId = (String) event.get("id");
            String type = (String) event.get("type");
            String userId = (String) event.get("userId");
            String ipAddress = (String) event.get("ipAddress");
            long time = ((Number) event.get("time")).longValue();

            AuditAction action = mapKeycloakAction(type);
            Outcome outcome = mapKeycloakOutcome(type);
            String actor = userId != null ? userId : "unknown";
            String actorType = userId != null ? ActorType.USER.name() : ActorType.SYSTEM.name();

            AuditCommand cmd = new AuditCommand(
                    UUID.randomUUID(),
                    Instant.ofEpochMilli(time),
                    actor,
                    ActorType.valueOf(actorType),
                    action,
                    TargetType.AUTH,
                    eventId,
                    outcome,
                    "KEYCLOAK",
                    eventId,
                    maskIp(ipAddress),
                    traceId,
                    Map.of("type", type, "userId", userId, "ipAddress", ipAddress)
            );

            try {
                int rows = entityManager.createNativeQuery(
                        "INSERT INTO audit_event (id, occurred_at, recorded_at, actor, actor_type, action, "
                                + "target_type, target_id, outcome, source, source_event_id, client_net, trace_id, detail) "
                                + "VALUES (:id, :occurredAt, now(), :actor, :actorType, :action, "
                                + ":targetType, :targetId, :outcome, :source, :sourceEventId, :clientNet, :traceId, "
                                + "cast(:detail as jsonb)) "
                                + "ON CONFLICT (source_event_id) WHERE source_event_id IS NOT NULL DO NOTHING")
                        .setParameter("id", cmd.id())
                        .setParameter("occurredAt", cmd.occurredAt())
                        .setParameter("actor", cmd.actor())
                        .setParameter("actorType", cmd.actorType().name())
                        .setParameter("action", cmd.action().name())
                        .setParameter("targetType", cmd.targetType().name())
                        .setParameter("targetId", cmd.targetId())
                        .setParameter("outcome", cmd.outcome().name())
                        .setParameter("source", cmd.source())
                        .setParameter("sourceEventId", cmd.sourceEventId())
                        .setParameter("clientNet", cmd.clientNet())
                        .setParameter("traceId", cmd.traceId())
                        .setParameter("detail", cmd.detail())
                        .executeUpdate();
                inserted += rows;
            } catch (Exception e) {
                System.err.println("keycloak_insert_error eventId=" + eventId + " msg=" + e.getMessage());
            }
        }
        return inserted;
    }

    private Map<String, Object> readCursor() {
        try {
            List<Map<String, Object>> rows = entityManager.createNativeQuery(
                    "SELECT last_event_time, last_event_id FROM keycloak_cursor WHERE id = :id")
                    .setParameter("id", REALM_EVENTS_ID)
                    .getResultList();
            if (rows.isEmpty()) {
                return Map.of("lastEventTime", 0L, "lastEventId", (String) null);
            }
            Map<String, Object> row = rows.get(0);
            return Map.of(
                    "lastEventTime", row.get("last_event_time"),
                    "lastEventId", row.get("last_event_id")
            );
        } catch (Exception e) {
            return Map.of("lastEventTime", 0L, "lastEventId", (String) null);
        }
    }

    private void upsertCursor(long lastEventTime, String lastEventId) {
        entityManager.createNativeQuery(
                "INSERT INTO keycloak_cursor (id, last_event_time, last_event_id, updated_at) "
                        + "VALUES (:id, :time, :eventId, now()) "
                        + "ON CONFLICT (id) DO UPDATE SET "
                        + "  last_event_time = greatest(keycloak_cursor.last_event_time, excluded.last_event_time), "
                        + "  last_event_id = CASE WHEN excluded.last_event_time >= keycloak_cursor.last_event_time "
                        + "                     THEN excluded.last_event_id ELSE keycloak_cursor.last_event_id END, "
                        + "  updated_at = now()")
                .setParameter("id", REALM_EVENTS_ID)
                .setParameter("time", lastEventTime)
                .setParameter("eventId", lastEventId)
                .executeUpdate();
    }

    private AuditAction mapKeycloakAction(String type) {
        switch (type) {
            case "LOGIN": return AuditAction.LOGIN;
            case "LOGOUT": return AuditAction.LOGOUT;
            case "LOGIN_ERROR": return AuditAction.LOGIN_FAILED;
            default: return AuditAction.ACCESS_DENIED;
        }
    }

    private Outcome mapKeycloakOutcome(String type) {
        if ("LOGIN_ERROR".equals(type)) {
            return Outcome.FAILED;
        }
        return Outcome.SUCCESS;
    }

    private String maskIp(String ip) {
        if (ip == null) return null;
        String[] parts = ip.split("\\.");
        if (parts.length == 4) {
            return parts[0] + "." + parts[1] + "." + parts[2] + ".0/24";
        }
        return ip;
    }
}