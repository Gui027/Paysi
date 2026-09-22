package com.paysi.payment.provider.asaas;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * Cliente HTTP dedicado à Asaas: URL raiz e o header {@code access_token} (RNF de
 * autenticação da Asaas) ficam fixos aqui em vez de repetidos em cada chamada do
 * {@link AsaasClient}. Só existe quando {@code paysi.provider=asaas} — em qualquer
 * outro provedor a chave sequer precisa estar presente no ambiente.
 */
@Configuration
@ConditionalOnProperty(name = "paysi.provider", havingValue = "asaas")
class AsaasConfiguration {
    @Bean
    RestTemplate asaasRestTemplate(RestTemplateBuilder builder,
            @Value("${paysi.asaas.base-url}") String baseUrl,
            @Value("${paysi.asaas.api-key:}") String apiKey) {
        if (apiKey.isBlank()) {
            throw new IllegalStateException(
                    "PAYSI_PROVIDER=asaas exige ASAAS_API_KEY configurada");
        }
        return builder
                .rootUri(baseUrl)
                .connectTimeout(Duration.ofSeconds(5))
                .readTimeout(Duration.ofSeconds(15))
                .defaultHeader("access_token", apiKey)
                .defaultHeader("User-Agent", "Paysi-Backend")
                .build();
    }
}
