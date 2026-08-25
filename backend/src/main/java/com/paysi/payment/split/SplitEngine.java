package com.paysi.payment.split;

import com.paysi.core.money.Bps;

import java.util.Objects;

/** Fonte única da divisão. Vendedor recebe o residual exato. */
public final class SplitEngine {
    private static final long PLATFORM_FIXED_FEE_CENTS = 200;
    private static final int MAX_COMMISSION_BPS = 5_000;

    private SplitEngine() {}

    public static Split split(long paidCents, PaymentMethod method, Plan plan, int commissionBps) {
        if (paidCents <= 0) throw new IllegalArgumentException("valor pago deve ser positivo");
        if (commissionBps < 0 || commissionBps > MAX_COMMISSION_BPS) {
            throw new IllegalArgumentException("comissão deve estar entre 0 e 5.000 bps");
        }
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(plan, "plan");

        long providerCost = addExact(trunc(paidCents, method.providerBps()), method.providerFixedCents());
        long sellerFee = addExact(trunc(paidCents, method.feeBps(plan)), PLATFORM_FIXED_FEE_CENTS);
        long affiliate = trunc(paidCents, commissionBps);
        long seller = Math.subtractExact(Math.subtractExact(paidCents, sellerFee), affiliate);
        long platformNet = Math.subtractExact(sellerFee, providerCost);

        Split result = new Split(seller, affiliate, platformNet, providerCost, sellerFee);
        if (seller < 0 || affiliate < 0 || providerCost < 0 || result.allocatedCents() != paidCents) {
            throw new IllegalArgumentException("divisão inválida para os parâmetros informados");
        }
        return result;
    }

    static long trunc(long cents, int bps) {
        return new Bps(bps).applyTo(cents);
    }

    private static long addExact(long left, long right) {
        return Math.addExact(left, right);
    }
}

