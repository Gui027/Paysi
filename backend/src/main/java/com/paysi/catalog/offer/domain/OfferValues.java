package com.paysi.catalog.offer.domain;

import java.util.Set;

public record OfferValues(
        long priceCents,
        BillingCycle cycle,
        int trialDays,
        boolean trialRequiresCard,
        int guaranteeDays,
        int maxInstallments,
        int boletoDueDays,
        int boletoAdvanceDays,
        Set<OfferPaymentMethod> paymentMethods,
        OfferPayoutDelay payoutDelay
) {
}
