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
        String name,
        String returnUrl
) {
    public static final int NAME_MAX_LENGTH = 60;
    public static final int RETURN_URL_MAX_LENGTH = 500;

    public OfferValues {
        if (returnUrl != null) {
            returnUrl = returnUrl.strip();
            if (returnUrl.isEmpty()) returnUrl = null;
            else validateReturnUrl(returnUrl);
        }
        if (name != null) {
            name = name.strip();
            if (name.isEmpty()) name = null;
            else if (name.length() > NAME_MAX_LENGTH) {
                throw new com.paysi.core.error.ValidationException("OFFER_INVALID",
                        "O nome da oferta deve ter no máximo 60 caracteres", "name");
            }
        }
    }

    /** Aceita só https (ou http em localhost, para testes), sem usuário/senha embutidos. */
    static void validateReturnUrl(String value) {
        java.net.URI uri;
        try {
            uri = java.net.URI.create(value);
        } catch (IllegalArgumentException error) {
            throw invalidReturnUrl();
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(java.util.Locale.ROOT);
        String host = uri.getHost();
        boolean local = host != null && (host.equals("localhost") || host.equals("127.0.0.1"));
        boolean allowed = scheme.equals("https") || (scheme.equals("http") && local);
        if (value.length() > RETURN_URL_MAX_LENGTH || !allowed || host == null || uri.getUserInfo() != null) {
            throw invalidReturnUrl();
        }
    }

    private static com.paysi.core.error.ValidationException invalidReturnUrl() {
        return new com.paysi.core.error.ValidationException("OFFER_INVALID",
                "A URL de retorno deve começar com https:// e ter no máximo 500 caracteres", "returnUrl");
    }

    /** Oferta sem URL de retorno. */
    public OfferValues(long priceCents, BillingCycle cycle, int trialDays, boolean trialRequiresCard,
                       int guaranteeDays, int maxInstallments, int boletoDueDays, int boletoAdvanceDays,
                       Set<OfferPaymentMethod> paymentMethods, OfferPayoutDelay payoutDelay, String name) {
        this(priceCents, cycle, trialDays, trialRequiresCard, guaranteeDays, maxInstallments, boletoDueDays,
                boletoAdvanceDays, paymentMethods, payoutDelay, name, null);
    }

    /** Oferta sem nome (o painel exibe "Oferta 1", "Oferta 2"…). */
    public OfferValues(long priceCents, BillingCycle cycle, int trialDays, boolean trialRequiresCard,
                       int guaranteeDays, int maxInstallments, int boletoDueDays, int boletoAdvanceDays,
                       Set<OfferPaymentMethod> paymentMethods, OfferPayoutDelay payoutDelay) {
        this(priceCents, cycle, trialDays, trialRequiresCard, guaranteeDays, maxInstallments, boletoDueDays,
                boletoAdvanceDays, paymentMethods, payoutDelay, null, null);
    }
}
