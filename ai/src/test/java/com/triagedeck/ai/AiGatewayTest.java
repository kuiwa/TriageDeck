package com.triagedeck.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiGatewayTest {

    private static AiGateway gateway(boolean enabled, boolean mock, String baseUrl, String apiKey) {
        return new AiGateway(new ObjectMapper(), enabled, mock, baseUrl, apiKey,
                "gpt-4o-mini", Duration.ofSeconds(1));
    }

    @Test
    void should_not_configure_when_mock() {
        assertFalse(gateway(true, true, "http://model", "key").isConfigured());
    }

    @Test
    void should_not_configure_when_api_key_missing() {
        assertFalse(gateway(true, false, "http://model", "").isConfigured());
    }

    @Test
    void should_not_configure_when_base_url_missing() {
        assertFalse(gateway(true, false, "", "key").isConfigured());
    }

    @Test
    void should_configure_when_all_present() {
        assertTrue(gateway(true, false, "http://model", "key").isConfigured());
    }

    @Test
    void should_return_empty_filter_without_network_when_unconfigured() {
        assertTrue(gateway(true, false, "", "").toFilterJson("严重告警").isEmpty());
    }

    @Test
    void should_respect_enabled_flag() {
        assertFalse(gateway(false, false, "http://model", "key").isEnabled());
    }
}