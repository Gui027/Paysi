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
        OfferPayoutDelay payoutDelay,
        String name
) {
    public static final int NAME_MAX_LENGTH = 60;

    public OfferValues {
        if (name != null) {
            name = name.strip();
            if (name.isEmpty()) name = null;
            else if (name.length() > NAME_MAX_LENGTH) {
                throw new com.paysi.core.error.ValidationException("OFFER_INVALID",
                        "O nome da oferta deve ter no máximo 60 caracteres", "name");
            }
        }
    }

    /** Oferta sem nome (o painel exibe "Oferta 1", "Oferta 2"…). */
    public OfferValues(long priceCents, BillingCycle cycle, int trialDays, boolean trialRequiresCard,
                       int guaranteeDays, int maxInstallments, int boletoDueDays, int boletoAdvanceDays,
                       Set<OfferPaymentMethod> paymentMethods, OfferPayoutDelay payoutDelay) {
        this(priceCents, cycle, trialDays, trialRequiresCard, guaranteeDays, maxInstallments, boletoDueDays,
                boletoAdvanceDays, paymentMethods, payoutDelay, null);
    }
}
