package com.paysi.catalog.offer.web.dto;

import com.paysi.catalog.offer.app.OfferPublication;
import com.paysi.catalog.offer.app.PublicationAction;

public record OfferPublicationResponse(
        boolean published,
        PublicationAction requiredAction,
        String actionUrl,
        OfferResponse offer
) {
    public static OfferPublicationResponse from(OfferPublication publication) {
        return new OfferPublicationResponse(publication.published(), publication.requiredAction(),
                publication.actionUrl(), OfferResponse.from(publication.offer()));
    }
}
