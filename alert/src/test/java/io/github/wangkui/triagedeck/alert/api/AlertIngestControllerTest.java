package io.github.wangkui.triagedeck.alert.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import io.github.wangkui.triagedeck.alert.domain.AlertIngestCommand;
import io.github.wangkui.triagedeck.alert.domain.AlertIngestResult;
import io.github.wangkui.triagedeck.alert.domain.Ingress;
import io.github.wangkui.triagedeck.alert.domain.Severity;
import io.github.wangkui.triagedeck.alert.service.AlertIngestService;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class AlertIngestControllerTest {

    private final AlertIngestService ingestService = mock(AlertIngestService.class);
    private final LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new AlertIngestController(ingestService))
                .setControllerAdvice(new AlertIngestExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @AfterEach
    void tearDown() {
        validator.close();
    }

    @Test
    void should_return_202_receipt_when_ingest_succeeds() throws Exception {
        UUID id = UUID.randomUUID();
        Instant lastSeenAt = Instant.parse("2026-11-25T06:00:00Z");
        given(ingestService.ingest(any(AlertIngestCommand.class), eq(Ingress.HTTP)))
                .willReturn(new AlertIngestResult(id, true, 1, Severity.CRITICAL, lastSeenAt));

        mockMvc.perform(post("/api/alerts/ingest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "source": "payment-api",
                                  "type": "5xx",
                                  "resourceId": "pay-01",
                                  "eventCode": "RULE_503",
                                  "severity": "CRITICAL",
                                  "message": "HTTP 503",
                                  "occurredAt": "2026-11-25T06:00:00Z",
                                  "payload": {"count": 12}
                                }
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.created").value(true))
                .andExpect(jsonPath("$.occurrenceCount").value(1))
                .andExpect(jsonPath("$.severity").value("CRITICAL"))
                .andExpect(jsonPath("$.lastSeenAt").value("2026-11-25T06:00:00Z"));

        verify(ingestService).ingest(any(AlertIngestCommand.class), eq(Ingress.HTTP));
    }

    @Test
    void should_return_validation_envelope_when_required_field_is_missing() throws Exception {
        mockMvc.perform(post("/api/alerts/ingest")
                        .header("X-Trace-Id", "ingest-validation-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "5xx",
                                  "resourceId": "pay-01",
                                  "severity": "CRITICAL",
                                  "message": "HTTP 503",
                                  "occurredAt": "2026-11-25T06:00:00Z"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.traceId").value("ingest-validation-1"));
    }
}
