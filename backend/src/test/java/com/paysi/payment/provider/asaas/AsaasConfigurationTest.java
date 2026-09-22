package com.paysi.payment.provider.asaas;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.web.client.RestTemplateAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Garante que ligar {@code paysi.provider=asaas} não gera dois beans {@link RestTemplate}
 * ambíguos (o padrão da aplicação, mais o {@code asaasRestTemplate}) e que o
 * {@link AsaasClient} resolve o qualificado corretamente. Não sobe o contexto completo
 * (sem banco/Testcontainers) — só a fatia relevante de configuração HTTP.
 */
class AsaasConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RestTemplateAutoConfiguration.class, JacksonAutoConfiguration.class))
            .withUserConfiguration(AnotherRestTemplateBean.class, AsaasConfiguration.class, AsaasClient.class)
            .withPropertyValues("paysi.provider=asaas", "paysi.asaas.api-key=test-key",
                    "paysi.asaas.base-url=https://api-sandbox.asaas.com/v3");

    @Test
    void resolvesQualifiedRestTemplateDespiteAnotherRestTemplateBeanExisting() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBeansOfType(RestTemplate.class)).hasSize(2);
            assertThat(context.getBean(AsaasClient.class)).isNotNull();
        });
    }

    @Test
    void missingApiKeyFailsFastInsteadOfCallingAsaasWithBlankToken() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(RestTemplateAutoConfiguration.class))
                .withUserConfiguration(AsaasConfiguration.class)
                .withPropertyValues("paysi.provider=asaas", "paysi.asaas.api-key=",
                        "paysi.asaas.base-url=https://api-sandbox.asaas.com/v3")
                .run(context -> assertThat(context).hasFailed());
    }

    @Configuration
    static class AnotherRestTemplateBean {
        @Bean
        RestTemplate restTemplate() {
            return new RestTemplate();
        }
    }
}
