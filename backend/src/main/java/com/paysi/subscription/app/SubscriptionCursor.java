package com.paysi.subscription.app;

import java.time.Instant;
import java.util.UUID;

public record SubscriptionCursor(Instant createdAt, UUID id) {
}
