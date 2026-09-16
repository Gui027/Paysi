package com.paysi.subscription.app;

public record CreateSubscriptionCommand(
        String offerSlug,
        String buyerName,
        String buyerEmail,
        String personType,
        String taxId,
        String legalName,
        String municipalReg,
        String addressJson,
        String cardToken,
        String idempotencyKey
) {
}
