package com.paysi.subscription.web.dto;

import com.paysi.subscription.app.CreateSubscriptionCommand;
import jakarta.validation.constraints.NotBlank;

public record SubscriptionCreateRequest(
        @NotBlank String buyerName,
        @NotBlank String buyerEmail,
        @NotBlank String personType,
        @NotBlank String taxId,
        String legalName,
        String municipalReg,
        String addressJson,
        String cardToken,
        String method,
        String visitorKey
) {
    public CreateSubscriptionCommand toCommand(String offerSlug, String idempotencyKey) {
        return new CreateSubscriptionCommand(offerSlug, buyerName, buyerEmail, personType, taxId,
                legalName, municipalReg, addressJson, cardToken, method, visitorKey, idempotencyKey);
    }
}
