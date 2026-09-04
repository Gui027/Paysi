package com.paysi.checkout.pub.app;

import com.paysi.catalog.offer.domain.BillingCycle;
import com.paysi.catalog.offer.domain.OfferPaymentMethod;
import com.paysi.catalog.product.domain.ChargeType;
import com.paysi.catalog.product.domain.Segment;
import com.paysi.identity.domain.PersonType;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record CheckoutContract(
        String product,
        Segment segment,
        ChargeType chargeType,
        long priceCents,
        BillingCycle cycle,
        Instant today,
        Instant nextChargeAt,
        Set<OfferPaymentMethod> methods,
        int installments,
        Map<PersonType, List<String>> requiredBuyerFields,
        Appearance appearance,
        LegalTexts legalTexts
) {
    public record Appearance(
            String logoUrl,
            String bannerUrl,
            String sideImageUrl,
            String primaryColor,
            String buttonText
    ) {
    }

    public record LegalTexts(
            String termsUrl,
            String privacyUrl
    ) {
    }
}
