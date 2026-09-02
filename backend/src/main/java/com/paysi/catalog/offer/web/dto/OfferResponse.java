package com.paysi.catalog.offer.web.dto;

import com.paysi.catalog.offer.app.OfferView;
import com.paysi.catalog.offer.domain.BillingCycle;
import com.paysi.catalog.offer.domain.OfferPaymentMethod;
import com.paysi.catalog.offer.domain.OfferPayoutDelay;
import com.paysi.catalog.offer.domain.OfferStatus;
import com.paysi.catalog.product.domain.ChargeType;
import com.paysi.catalog.product.domain.Segment;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record OfferResponse(
        UUID id,
        UUID productId,
        ChargeType chargeType,
        Segment segment,
        String slug,
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
        OfferStatus status,
        Instant availableAt,
        Instant createdAt,
        Instant updatedAt
) {
    public static OfferResponse from(OfferView view) {
        var offer = view.offer();
        return new OfferResponse(offer.id(), offer.productId(), offer.chargeType(), offer.segment(),
                offer.slug(), offer.priceCents(), offer.cycle(), offer.trialDays(),
                offer.trialRequiresCard(), offer.guaranteeDays(), offer.maxInstallments(),
                offer.boletoDueDays(), offer.boletoAdvanceDays(), offer.paymentMethods(),
                offer.payoutDelay(), offer.status(), view.availableAt(), offer.createdAt(), offer.updatedAt());
    }
}
