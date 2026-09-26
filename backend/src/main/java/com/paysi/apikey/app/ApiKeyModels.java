package com.paysi.apikey.app;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Modelos das chaves da API pública. */
public final class ApiKeyModels {
    private ApiKeyModels() { }

    public static final String REPORTS = "reports";
    public static final String PRODUCTS = "products";
    public static final String SALES = "sales";
    public static final String SALES_REFUND = "sales_refund";
    public static final String AFFILIATES = "affiliates";
    public static final String FINANCE = "finance";
    public static final String WEBHOOKS = "webhooks";
    public static final Set<String> SCOPES = Set.of(REPORTS, PRODUCTS, SALES, SALES_REFUND, AFFILIATES, FINANCE, WEBHOOKS);

    /** {@code keyHint} é o que a lista mostra ("*****4953"); o segredo completo nunca volta depois da criação. */
    public record ApiKey(UUID id, String name, String keyHint, List<String> scopes, Instant createdAt, Instant lastUsedAt,
                         UUID clientId, UUID accountId) { }

    public record CreatedApiKey(ApiKey key, String clientSecret) { }

    /** Dados de uma chave para conferir o segredo na emissão do token. */
    public record KeyCredentials(UUID id, UUID accountId, String secretHash, List<String> scopes) { }

    public record TokenGrant(UUID keyId, UUID accountId, List<String> scopes, Instant expiresAt) { }

    public record AccessToken(@JsonProperty("access_token") String accessToken, @JsonProperty("token_type") String tokenType,
                              @JsonProperty("expires_in") long expiresIn, String scope) { }

    public record ApiPrincipal(UUID accountId, UUID keyId, List<String> scopes) { }
}
