package io.github.wangkui.triagedeck.app.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * Keycloak 审计采集器所需的 HTTP 客户端（Spring Boot 只自动配置 RestTemplateBuilder）。
 */
@Configuration
public class HttpClientConfig {

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
