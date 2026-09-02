package com.paysi.catalog.offer.domain;

public enum OfferPayoutDelay {
    D32(32),
    D15(15),
    D7(7),
    D2(2);

    private final int days;

    OfferPayoutDelay(int days) {
        this.days = days;
    }

    public int days() {
        return days;
    }
}
