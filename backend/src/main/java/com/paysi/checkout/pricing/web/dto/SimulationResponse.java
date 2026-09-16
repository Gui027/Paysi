package com.paysi.checkout.pricing.web.dto;

import com.paysi.catalog.offer.domain.OfferPaymentMethod;
import com.paysi.checkout.pricing.app.PriceQuote;

import java.time.Instant;

/** Memória de cálculo devolvida ao checkout. O cliente formata; não recalcula. */
public record SimulationResponse(
        long grossCents,
        long discountCents,
        long paidCents,
        OfferPaymentMethod method,
        int installments,
        String couponCode,
        long feesCents,
        long commissionCents,
        long sellerCents,
        Instant availableAt
) {
    public static SimulationResponse from(PriceQuote quote) {
        return new SimulationResponse(quote.grossCents(), quote.discountCents(), quote.paidCents(),
                quote.method(), quote.installments(), quote.couponCode(), quote.feesCents(),
                quote.commissionCents(), quote.sellerCents(), quote.availableAt());
    }
}
