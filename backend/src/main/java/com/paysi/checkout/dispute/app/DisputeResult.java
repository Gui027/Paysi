package com.paysi.checkout.dispute.app;

import java.time.Instant;
import java.util.UUID;

public record DisputeResult(UUID disputeId, UUID chargeId, String status, long sellerCents, long affiliateCents,
                             Instant deadlineAt, boolean idempotentReplay) {
}
