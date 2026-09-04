package com.paysi.catalog.coupon.domain;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record CouponValues(
        CouponKind kind,
        int value,
        Instant startsAt,
        Instant expiresAt,
        Integer maxRedemptions,
        int maxPerBuyer,
        Set<UUID> offerIds
) {
}
