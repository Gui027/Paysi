package com.paysi.dashboard.app;

import java.time.Instant;
import java.util.UUID;

public record RecentSale(UUID id, String buyer, long amountCents, String method, String status,
                         Instant occurredAt) {
}
