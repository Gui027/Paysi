package com.paysi.checkout.refund.app;

public record RefundCommand(Long amountCents, String reason, String idempotencyKey) {
}
