package com.paysi.checkout.pub.web.dto;

import com.paysi.catalog.offer.domain.BillingCycle;
import com.paysi.catalog.offer.domain.OfferPaymentMethod;
import com.paysi.catalog.product.domain.ChargeType;
import com.paysi.catalog.product.domain.Segment;
import com.paysi.checkout.pub.app.CheckoutContract;
import com.paysi.identity.domain.PersonType;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record CheckoutResponse(
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
        AppearanceResponse appearance,
        LegalTextsResponse legalTexts
) {
    public static CheckoutResponse from(CheckoutContract contract) {
        return new CheckoutResponse(contract.product(), contract.segment(), contract.chargeType(),
                contract.priceCents(), contract.cycle(), contract.today(), contract.nextChargeAt(),
                contract.methods(), contract.installments(), contract.requiredBuyerFields(),
                AppearanceResponse.from(contract.appearance()), LegalTextsResponse.from(contract.legalTexts()));
    }

    public record AppearanceResponse(
            String logoUrl,
            String bannerUrl,
            String sideImageUrl,
            String primaryColor,
            String buttonText
    ) {
        static AppearanceResponse from(CheckoutContract.Appearance appearance) {
            return new AppearanceResponse(appearance.logoUrl(), appearance.bannerUrl(),
                    appearance.sideImageUrl(), appearance.primaryColor(), appearance.buttonText());
        }
    }

    public record LegalTextsResponse(
            String termsUrl,
            String privacyUrl
    ) {
        static LegalTextsResponse from(CheckoutContract.LegalTexts legalTexts) {
            return new LegalTextsResponse(legalTexts.termsUrl(), legalTexts.privacyUrl());
        }
    }
}
