package com.triagedeck.simulator;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;

/**
 * 网元模拟器：把告警上报投到 `alert.ingested`（Kafka 入口），或退回 HTTP `#5`。
 *
 * <pre>
 * ./gradlew :device-simulator:run --args="--count=10 --mode=kafka"
 * ./gradlew :device-simulator:run --args="--count=5 --mode=http --token=$AUTH_TOKEN"
 * </pre>
 *
 * 消息体字段与 `02b-contract.md §6.3` 的 `AlertIngestedMessage` 完全一致；
 * 发送顺序按 3 个资源轮转，因此 count&gt;3 时会出现同 fingerprint 的重复上报（用于验证 upsert/计数/升级）。
 */
public final class DeviceSimulator {

    private static final String[] SEVERITIES = {"CRITICAL", "HIGH", "MEDIUM"};
    private static final String[] EVENT_CODES = {"CPU_HIGH", "LINK_DOWN", "MEMORY_HIGH"};
    private static final String SOURCE = "device-simulator";

    private DeviceSimulator() {
    }

    public static void main(String[] args) throws Exception {
        Options options = Options.parse(args);
        switch (options.mode()) {
            case "kafka" -> runKafka(options);
            case "http" -> runHttp(options);
            default -> {
                System.err.println("未知 --mode=" + options.mode() + "（支持 kafka / http）");
                System.exit(2);
            }
        }
    }

    private static void runKafka(Options options) throws Exception {
        Properties properties = new Properties();
        properties.put("bootstrap.servers", options.bootstrap());
        properties.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        properties.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        properties.put("acks", "all");
        properties.put("client.id", "device-simulator");

        try (KafkaProducer<String, String> producer = new KafkaProducer<>(properties)) {
            for (int index = 0; index < options.count(); index++) {
                AlertSample sample = sample(index);
                String key = fingerprint(sample);
                producer.send(new ProducerRecord<>(options.topic(), key, sample.toJson()));
                System.out.printf("sent #%d key=%s resourceId=%s severity=%s%n",
                        index + 1, key.substring(0, 12), sample.resourceId(), sample.severity());
            }
            producer.flush();
        }
        System.out.printf("完成：%d 条已投递到 %s（%s）%n",
                options.count(), options.topic(), options.bootstrap());
    }

    private static void runHttp(Options options) throws Exception {
        if (options.token().isBlank()) {
            System.err.println("HTTP 模式需要 AUTH_TOKEN 环境变量或 --token=...");
            System.exit(2);
        }
        HttpClient client = HttpClient.newHttpClient();
        for (int index = 0; index < options.count(); index++) {
            AlertSample sample = sample(index);
            HttpRequest request = HttpRequest.newBuilder(
                            URI.create(options.baseUrl() + "/api/alerts/ingest"))
                    .header("Authorization", "Bearer " + options.token())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(sample.toJson(), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            System.out.printf("POST #%d -> %d%n", index + 1, response.statusCode());
        }
    }

    private static AlertSample sample(int index) {
        int slot = index % SEVERITIES.length;
        String resourceId = "device-" + (slot + 1);
        int metric = 80 + (index % 15);
        return new AlertSample(
                SOURCE,
                "threshold",
                resourceId,
                EVENT_CODES[slot],
                SEVERITIES[slot],
                resourceId + " metric " + metric + "%",
                Instant.now(),
                metric);
    }

    /**
     * 与 `alert` 模块 `Fingerprint.of` 同意：source/type/resourceId/eventCode 去空格小写后用 0x1F 连接，
     * 取 SHA-256 小写十六进制。复制而非依赖 alert 模块，避免把 Web/JPA 依赖拖进工具。
     */
    private static String fingerprint(AlertSample sample) throws NoSuchAlgorithmException {
        String material = String.join("\u001F",
                normalize(sample.source()),
                normalize(sample.type()),
                normalize(sample.resourceId()),
                normalize(sample.eventCode()));
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(material.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest);
    }

    private static String normalize(String value) {
        return Objects.toString(value, "").strip().toLowerCase(Locale.ROOT);
    }

    private record AlertSample(
            String source,
            String type,
            String resourceId,
            String eventCode,
            String severity,
            String message,
            Instant occurredAt,
            int metric) {

        String toJson() {
            return "{\"source\":\"" + source + "\",\"type\":\"" + type + "\",\"resourceId\":\""
                    + resourceId + "\",\"eventCode\":\"" + eventCode + "\",\"severity\":\"" + severity
                    + "\",\"message\":\"" + message + "\",\"occurredAt\":\"" + occurredAt
                    + "\",\"payload\":{\"metric\":" + metric + "}}";
        }
    }

    private record Options(
            String mode,
            int count,
            String bootstrap,
            String topic,
            String baseUrl,
            String token) {

        static Options parse(String[] args) {
            Map<String, String> values = new HashMap<>();
            for (String arg : args) {
                if (!arg.startsWith("--") || !arg.contains("=")) {
                    continue;
                }
                int separator = arg.indexOf('=');
                values.put(arg.substring(2, separator), arg.substring(separator + 1));
            }
            String mode = values.getOrDefault("mode", "kafka");
            int count = Integer.parseInt(values.getOrDefault("count", "10"));
            String bootstrap = values.getOrDefault("bootstrap", firstNonBlank(
                    System.getenv("KAFKA_BOOTSTRAP_SERVERS"),
                    System.getenv("KAFKA_BOOTSTRAP"),
                    "localhost:9092"));
            String topic = values.getOrDefault("topic", firstNonBlank(
                    System.getenv("KAFKA_TOPIC_INGESTED"),
                    "alert.ingested"));
            String baseUrl = values.getOrDefault("base-url", firstNonBlank(
                    System.getenv("BASE_URL"),
                    "http://localhost:8080"));
            String token = values.getOrDefault("token", firstNonBlank(
                    System.getenv("AUTH_TOKEN"),
                    ""));
            return new Options(mode, count, bootstrap, topic, baseUrl, token);
        }

        private static String firstNonBlank(String... candidates) {
            for (String candidate : candidates) {
                if (candidate != null && !candidate.isBlank()) {
                    return candidate;
                }
            }
            return "";
        }
    }
}
