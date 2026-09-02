package com.paysi.catalog.offer.app;

public record OfferPublication(
        boolean published,
        PublicationAction requiredAction,
        String actionUrl,
        OfferView offer
) {
    public static OfferPublication published(OfferView offer) {
        return new OfferPublication(true, PublicationAction.NONE, null, offer);
    }

    public static OfferPublication action(PublicationAction action, String actionUrl, OfferView offer) {
        return new OfferPublication(false, action, actionUrl, offer);
    }
}
