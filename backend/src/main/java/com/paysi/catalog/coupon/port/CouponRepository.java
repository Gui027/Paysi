package com.paysi.catalog.coupon.port;

import com.paysi.catalog.coupon.domain.Coupon;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CouponRepository {
    void insert(Coupon coupon);

    List<Coupon> listActiveOwned(UUID sellerId);

    Optional<Coupon> findActiveOwned(UUID sellerId, UUID couponId);

    default Optional<Coupon> findApplicableOwned(UUID sellerId, UUID offerId, String code) {
        return listActiveOwned(sellerId).stream()
                .filter(coupon -> coupon.code().equals(code) && coupon.offerIds().contains(offerId))
                .findFirst();
    }

    void update(Coupon coupon);

    boolean archive(UUID sellerId, UUID couponId, Instant archivedAt);
}
