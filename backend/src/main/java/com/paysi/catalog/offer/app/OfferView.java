package com.paysi.catalog.offer.app;

import com.paysi.catalog.offer.domain.Offer;

import java.time.Instant;
import java.util.Set;

public record OfferView(Offer offer, Instant availableAt, Set<OfferImmutableField> immutableFields) {
    public OfferView {
        immutableFields = immutableFields == null ? Set.of() : Set.copyOf(immutableFields);
    }

    public OfferView(Offer offer, Instant availableAt) {
        this(offer, availableAt, Set.of());
    }
}
