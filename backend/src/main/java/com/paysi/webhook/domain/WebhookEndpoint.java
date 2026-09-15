package com.paysi.webhook.domain;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record WebhookEndpoint(UUID id, UUID accountId, String url, Set<String> events, boolean enabled,
                              byte[] encryptedSecret, byte[] encryptedPreviousSecret,
                              Instant secretRotatedAt, Instant createdAt) { }
