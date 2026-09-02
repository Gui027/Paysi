package com.paysi.catalog.offer.app;

import com.paysi.catalog.offer.domain.Offer;

import java.time.Instant;

public record OfferView(Offer offer, Instant availableAt) {
}
