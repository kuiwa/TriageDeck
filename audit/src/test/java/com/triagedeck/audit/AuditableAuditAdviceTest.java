package com.triagedeck.audit;

import com.triagedeck.platform.audit.AuditAction;
import com.triagedeck.platform.audit.Auditable;
import com.triagedeck.platform.audit.Outcome;
import com.triagedeck.platform.audit.TargetType;
import com.triagedeck.platform.security.SecurityContext;
import com.triagedeck.platform.trace.TraceIds;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuditableAuditAdviceTest {

    static class Sample {
        private final UUID id;

        Sample(UUID id) {
            this.id = id;
        }

        public UUID currentId() {
            return id;
        }

        @Auditable(action = "ALERT_CLEARED", targetType = "ALERT", targetIdParam = "id")
        public String clear(UUID id) {
            return "ok";
        }

        @Auditable(action = "ALERT_CLEARED", targetType = "ALERT", targetIdParam = "id")
        public String clearFailing(UUID id) {
            throw new IllegalStateException("boom");
        }
    }

    static class RecordingAuditWriter implements AuditWriter {
        final List<AuditCommand> commands = new ArrayList<>();

        @Override
        public void write(AuditCommand command) {
            commands.add(command);
        }
    }

    @Configuration
    static class TestConfig {
        @Bean
        TraceIds traceIds() {
            return new TraceIds();
        }

        @Bean
        SecurityContext securityContext() {
            return new SecurityContext();
        }

        @Bean
        AuditWriter auditWriter() {
            return new RecordingAuditWriter();
        }

        @Bean
        AuditableAuditAdvice auditableAuditAdvice(AuditWriter writer, SecurityContext sc, TraceIds ids) {
            return new AuditableAuditAdvice(writer, sc, ids);
        }

        @Bean
        Sample sample() {
            return new Sample(UUID.randomUUID());
        }
    }

    private AnnotationConfigApplicationContext context() {
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
        ctx.register(AuditAopConfig.class, TestConfig.class);
        ctx.refresh();
        return ctx;
    }

    @Test
    void should_record_success_when_auditable_method_returns() {
        try (AnnotationConfigApplicationContext ctx = context()) {
            Sample sample = ctx.getBean(Sample.class);
            UUID id = UUID.randomUUID();

            sample.clear(id);

            RecordingAuditWriter writer = (RecordingAuditWriter) ctx.getBean(AuditWriter.class);
            assertEquals(1, writer.commands.size());
            AuditCommand cmd = writer.commands.get(0);
            assertEquals(AuditAction.ALERT_CLEARED, cmd.action());
            assertEquals(TargetType.ALERT, cmd.targetType());
            assertEquals(Outcome.SUCCESS, cmd.outcome());
            assertEquals(id.toString(), cmd.targetId());
            assertEquals("APP", cmd.source());
            assertEquals("SYSTEM", cmd.actor());
        }
    }

    @Test
    void should_record_failed_and_rethrow_when_auditable_method_throws() {
        try (AnnotationConfigApplicationContext ctx = context()) {
            Sample sample = ctx.getBean(Sample.class);

            assertThrows(IllegalStateException.class, () -> sample.clearFailing(UUID.randomUUID()));

            RecordingAuditWriter writer = (RecordingAuditWriter) ctx.getBean(AuditWriter.class);
            assertEquals(1, writer.commands.size());
            assertEquals(Outcome.FAILED, writer.commands.get(0).outcome());
        }
    }

    @Test
    void should_not_audit_non_annotated_method() {
        try (AnnotationConfigApplicationContext ctx = context()) {
            Sample sample = ctx.getBean(Sample.class);
            // currentId() 未标注 @Auditable，不应触发审计
            assertTrue(sample.currentId() != null);

            RecordingAuditWriter writer = (RecordingAuditWriter) ctx.getBean(AuditWriter.class);
            assertTrue(writer.commands.isEmpty());
        }
    }
}