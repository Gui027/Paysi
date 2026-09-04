package com.paysi.catalog.coupon.web.dto;

import com.paysi.catalog.coupon.domain.Coupon;
import com.paysi.catalog.coupon.domain.CouponKind;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record CouponResponse(
        UUID id,
        String code,
        CouponKind discountType,
        Integer discountBps,
        Integer discountCents,
        Instant startsAt,
        Instant expiresAt,
        Integer maxRedemptions,
        int maxPerBuyer,
        int redeemedCount,
        Set<UUID> offerIds,
        Instant createdAt
) {
    public static CouponResponse from(Coupon coupon) {
        Integer bps = coupon.kind() == CouponKind.PERCENT ? coupon.value() : null;
        Integer cents = coupon.kind() == CouponKind.FIXED ? coupon.value() : null;
        return new CouponResponse(coupon.id(), coupon.code(), coupon.kind(), bps, cents,
                coupon.startsAt(), coupon.expiresAt(), coupon.maxRedemptions(), coupon.maxPerBuyer(),
                coupon.redeemedCount(), coupon.offerIds(), coupon.createdAt());
    }
}
