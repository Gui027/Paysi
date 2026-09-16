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
        String method,
        String idempotencyKey
) {
    public String normalizedMethod() {
        return method == null || method.isBlank() ? "CARD" : method.toUpperCase(java.util.Locale.ROOT);
    }
}
