package com.paysi.webhook.domain;

import java.time.Instant;
import java.util.UUID;

public record OutboxEvent(UUID id, UUID accountId, String type, String payload, Instant createdAt) { }
