package com.paysi.dashboard.app;

import java.time.Instant;

public record UpcomingReceivable(long amountCents, Instant availableAt) {
}
