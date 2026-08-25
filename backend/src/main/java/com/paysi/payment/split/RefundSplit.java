package com.paysi.payment.split;

public final class RefundSplit {
    private RefundSplit() {}

    /** Trunca sobre o acumulado para impedir deriva entre reembolsos parciais. */
    public static RefundPart slice(Split original, long paidCents, long alreadyRefundedCents, long sliceCents) {
        if (paidCents <= 0 || alreadyRefundedCents < 0 || sliceCents <= 0) {
            throw new IllegalArgumentException("valores de reembolso inválidos");
        }
        long accumulated = Math.addExact(alreadyRefundedCents, sliceCents);
        if (accumulated > paidCents) throw new IllegalArgumentException("reembolso excede o valor pago");

        long affiliate = delta(original.affiliateCents(), alreadyRefundedCents, accumulated, paidCents);
        long platform = delta(original.platformNetCents(), alreadyRefundedCents, accumulated, paidCents);
        long provider = delta(original.providerCostCents(), alreadyRefundedCents, accumulated, paidCents);
        long sellerBefore = alreadyRefundedCents
                - proportional(original.affiliateCents(), alreadyRefundedCents, paidCents)
                - proportional(original.platformNetCents(), alreadyRefundedCents, paidCents)
                - proportional(original.providerCostCents(), alreadyRefundedCents, paidCents);
        long sellerAfter = accumulated
                - proportional(original.affiliateCents(), accumulated, paidCents)
                - proportional(original.platformNetCents(), accumulated, paidCents)
                - proportional(original.providerCostCents(), accumulated, paidCents);

        RefundPart result = new RefundPart(sellerAfter - sellerBefore, affiliate, platform, provider);
        if (result.sellerCents() < 0 || result.affiliateCents() < 0
                || result.platformCents() < 0 || result.providerCents() < 0
                || result.totalCents() != sliceCents) {
            throw new IllegalStateException("alocação de reembolso inválida");
        }
        return result;
    }

    private static long delta(long allocation, long before, long after, long paid) {
        return proportional(allocation, after, paid) - proportional(allocation, before, paid);
    }

    private static long proportional(long allocation, long accumulated, long paid) {
        return Math.multiplyExact(allocation, accumulated) / paid;
    }
}

