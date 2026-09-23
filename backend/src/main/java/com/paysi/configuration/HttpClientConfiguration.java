package com.paysi.configuration;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * Cliente HTTP de saída para integrações leves da plataforma (hoje: entrega de
 * alerta operacional no Slack). Timeout curto de propósito — uma entrega de
 * alerta não pode segurar a thread do job que a disparou.
 *
 * <p>{@code @Primary} porque com {@code paysi.provider=asaas} também existe o
 * {@code asaasRestTemplate} (ver AsaasConfiguration) — sem isso, qualquer bean que
 * pedir {@code RestTemplate} sem {@code @Qualifier} (como o {@code SlackAlertChannel})
 * quebra a subida do Spring por ambiguidade.
 */
@Configuration
class HttpClientConfiguration {
    @Bean
    @Primary
    RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .connectTimeout(Duration.ofSeconds(2))
                .readTimeout(Duration.ofSeconds(3))
                .build();
    }
}
