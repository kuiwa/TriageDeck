package io.github.wangkui.triagedeck.alert;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.wangkui.triagedeck.alert.domain.AlertIngestCommand;
import io.github.wangkui.triagedeck.alert.domain.AlertIngestResult;
import io.github.wangkui.triagedeck.alert.domain.Ingress;
import io.github.wangkui.triagedeck.alert.domain.Severity;
import io.github.wangkui.triagedeck.alert.repo.AlertIngestRepository;
import io.github.wangkui.triagedeck.alert.service.AlertIngestService;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.containers.PostgreSQLContainer;

class AlertIngestIT {

    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16");

    private static DriverManagerDataSource dataSource;
    private static JdbcTemplate jdbc;
    private static AlertIngestService service;
    private static TransactionTemplate transactions;

    @BeforeAll
    static void startDatabase() throws SQLException {
        POSTGRES.start();
        dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword());
        dataSource.setDriverClassName("org.postgresql.Driver");
        jdbc = new JdbcTemplate(dataSource);
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("V1__init.sql"));
        }
        service = new AlertIngestService(
                new AlertIngestRepository(new NamedParameterJdbcTemplate(dataSource)));
        transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    @AfterAll
    static void stopDatabase() {
        POSTGRES.stop();
    }

    @BeforeEach
    void clearAlerts() {
        jdbc.update("delete from alert_event");
        jdbc.update("delete from alerts_active");
    }

    @Test
    void should_merge_twelve_occurrences_and_set_created_flag() {
        AlertIngestResult first = ingest(command("resource-1", Severity.HIGH, "first"));
        AlertIngestResult latest = first;
        for (int i = 1; i < 12; i++) {
            latest = ingest(command("resource-1", Severity.HIGH, "message-" + i));
        }

        assertThat(first.created()).isTrue();
        assertThat(first.occurrenceCount()).isEqualTo(1);
        assertThat(latest.created()).isFalse();
        assertThat(latest.id()).isEqualTo(first.id());
        assertThat(latest.occurrenceCount()).isEqualTo(12);
        assertThat(jdbc.queryForObject("select count(*) from alerts_active", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from alert_event", Integer.class)).isEqualTo(12);
        assertThat(jdbc.queryForObject(
                "select message from alerts_active where id = ?",
                String.class,
                first.id())).isEqualTo("message-11");
    }

    @Test
    void should_upgrade_severity_without_downgrading() {
        AlertIngestResult high = ingest(command("resource-1", Severity.HIGH, "high"));
        AlertIngestResult critical = ingest(command("resource-1", Severity.CRITICAL, "critical"));
        AlertIngestResult low = ingest(command("resource-1", Severity.LOW, "low"));

        assertThat(critical.id()).isEqualTo(high.id());
        assertThat(critical.severity()).isEqualTo(Severity.CRITICAL);
        assertThat(low.severity()).isEqualTo(Severity.CRITICAL);
        assertThat(low.occurrenceCount()).isEqualTo(3);
    }

    @Test
    void should_create_a_new_row_when_resource_changes() {
        AlertIngestResult first = ingest(command("resource-1", Severity.MEDIUM, "same"));
        AlertIngestResult second = ingest(command("resource-2", Severity.MEDIUM, "same"));

        assertThat(second.id()).isNotEqualTo(first.id());
        assertThat(jdbc.queryForObject("select count(*) from alerts_active", Integer.class)).isEqualTo(2);
    }

    @Test
    void should_keep_only_one_row_under_twenty_concurrent_ingests() throws Exception {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<java.util.concurrent.Future<AlertIngestResult>> futures = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                futures.add(executor.submit(() -> ingest(command("resource-1", Severity.MEDIUM, "parallel"))));
            }
            for (var future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        }

        assertThat(jdbc.queryForObject("select count(*) from alerts_active", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select occurrence_count from alerts_active",
                Integer.class)).isEqualTo(20);
        assertThat(jdbc.queryForObject("select count(*) from alert_event", Integer.class)).isEqualTo(20);
    }

    @Test
    void should_keep_only_the_fifty_most_recent_events() {
        AlertIngestResult result = ingest(command("resource-1", Severity.MEDIUM, "event-0"));
        for (int i = 1; i < 51; i++) {
            result = ingest(command("resource-1", Severity.MEDIUM, "event-" + i));
        }

        assertThat(result.occurrenceCount()).isEqualTo(51);
        assertThat(jdbc.queryForObject(
                "select count(*) from alert_event where alert_id = ?",
                Integer.class,
                result.id())).isEqualTo(50);
    }

    @Test
    void should_rollback_aggregate_when_event_insert_fails() {
        jdbc.execute("""
                alter table alert_event
                add constraint ck_test_reject_http check (ingress <> 'HTTP')
                """);
        try {
            org.assertj.core.api.Assertions.assertThatThrownBy(
                            () -> ingest(command("resource-1", Severity.MEDIUM, "will fail")))
                    .isInstanceOf(DataIntegrityViolationException.class);
        } finally {
            jdbc.execute("alter table alert_event drop constraint ck_test_reject_http");
        }
        assertThat(jdbc.queryForObject("select count(*) from alerts_active", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from alert_event", Integer.class)).isZero();
    }

    private static AlertIngestResult ingest(AlertIngestCommand command) {
        return Objects.requireNonNull(transactions.execute(
                status -> service.ingest(command, Ingress.HTTP)));
    }

    private static AlertIngestCommand command(String resourceId, Severity severity, String message) {
        JsonNode payload = new ObjectMapper().createObjectNode().put("attempt", 1);
        return new AlertIngestCommand(
                "payment-api",
                "5xx",
                resourceId,
                "RULE_503",
                severity,
                message,
                Instant.now(),
                payload);
    }
}
