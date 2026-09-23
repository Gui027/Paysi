package com.paysi.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * O checkout (web-checkout) é uma SPA estática servida por outro domínio que chama a API
 * direto do navegador — sem isso o Spring rejeita todo preflight com "Invalid CORS request"
 * (comportamento padrão do framework sem nenhuma origem configurada, não é algo que a Paysi
 * escreveu). O painel não precisa disso: ele chama a API pelo seu próprio servidor Next.js
 * ({@code /api/*}), então do navegador é tudo mesma origem.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {
    private final List<String> allowedOrigins;

    public CorsConfig(@Value("${paysi.security.allowed-origins:http://localhost:5173}") String allowedOrigins) {
        this.allowedOrigins = List.of(allowedOrigins.split(","));
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/v1/**")
                .allowedOrigins(allowedOrigins.toArray(new String[0]))
                .allowedMethods("GET", "POST", "PUT", "DELETE")
                .allowedHeaders("Content-Type", "X-Correlation-Id")
                .allowCredentials(false);
    }
}
