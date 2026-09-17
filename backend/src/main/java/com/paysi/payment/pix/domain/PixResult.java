package com.paysi.payment.pix.domain;

import java.time.Instant;

public record PixResult(String providerChargeId, String qrCode, Instant expiresAt, String status,
                        boolean idempotentReplay) {
}
