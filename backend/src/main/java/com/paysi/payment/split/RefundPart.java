package com.paysi.payment.split;

public record RefundPart(long sellerCents, long affiliateCents, long platformCents, long providerCents) {
    public long totalCents() {
        return Math.addExact(
                Math.addExact(sellerCents, affiliateCents),
                Math.addExact(platformCents, providerCents));
    }
}

