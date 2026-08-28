package com.paysi.catalog.product.app;

import java.time.Instant;
import java.util.UUID;

public record ProductCursor(Instant createdAt, UUID id) {
}
