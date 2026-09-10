package com.paysi.affiliate.app;

import java.time.Instant;
import java.util.UUID;

public record AffiliateCursor(Instant createdAt, UUID id) {
}
