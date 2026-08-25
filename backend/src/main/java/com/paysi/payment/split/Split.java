package com.paysi.payment.split;

public record Split(
        long sellerCents,
        long affiliateCents,
        long platformNetCents,
        long providerCostCents,
        long sellerFeeCents
) {
    public long allocatedCents() {
        return Math.addExact(
                Math.addExact(sellerCents, affiliateCents),
                Math.addExact(platformNetCents, providerCostCents));
    }
}

