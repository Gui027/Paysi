package com.paysi.payment.receivable.domain;

import java.time.Instant;
import java.util.UUID;

public record Receivable(UUID id, UUID chargeId, int sequence, String providerId, Instant expectedAt,
                         long amountCents, long sellerAmountCents, long affiliateAmountCents) { }
