package com.paysi.catalog.offer.app;

import com.paysi.catalog.offer.domain.Offer;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

public final class OfferAvailability {
    private OfferAvailability() { }

    public static Instant at(Offer offer, Instant now) {
        long days = Math.max(offer.payoutDelay().days(), offer.guaranteeDays());
        return now.plus(days, ChronoUnit.DAYS);
    }
}
