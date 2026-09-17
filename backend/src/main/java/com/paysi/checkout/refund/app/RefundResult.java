package com.paysi.checkout.refund.app;

import java.util.UUID;

public record RefundResult(UUID refundId, String status, long sellerCents, long affiliateCents,
                            long platformCents, long providerCents, long chargeRefundedCents,
                            String chargeStatus, boolean idempotentReplay) {
}
