package com.paysi.webhook.domain;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** {@code productId} nulo = o webhook recebe os eventos de todos os produtos do vendedor. */
public record WebhookEndpoint(UUID id, UUID accountId, String url, Set<String> events, boolean enabled,
                              byte[] encryptedSecret, byte[] encryptedPreviousSecret,
                              Instant secretRotatedAt, Instant createdAt, String name, UUID productId) {
    public WebhookEndpoint(UUID id, UUID accountId, String url, Set<String> events, boolean enabled,
                           byte[] encryptedSecret, byte[] encryptedPreviousSecret,
                           Instant secretRotatedAt, Instant createdAt) {
        this(id, accountId, url, events, enabled, encryptedSecret, encryptedPreviousSecret, secretRotatedAt, createdAt, null, null);
    }
}
