package com.paysi.webhook.domain;

import java.time.Instant;
import java.util.UUID;

public record WebhookDelivery(UUID id, UUID eventId, UUID endpointId, int attempt, Integer httpStatus,
                              String error, Instant nextRetryAt, Instant createdAt) { }
