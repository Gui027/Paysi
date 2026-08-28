package com.paysi.payment.provider;

public record ProviderSplit(long sellerCents, long affiliateCents, long platformCents) {
    public ProviderSplit {
        if (sellerCents < 0 || affiliateCents < 0 || platformCents < 0) {
            throw new IllegalArgumentException("Split não aceita valores negativos");
        }
    }

    public long totalCents() {
        return Math.addExact(Math.addExact(sellerCents, affiliateCents), platformCents);
    }
}
