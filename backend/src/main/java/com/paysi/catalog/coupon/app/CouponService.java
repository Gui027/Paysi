package com.paysi.catalog.coupon.app;

import com.paysi.catalog.coupon.domain.Coupon;
import com.paysi.catalog.coupon.domain.CouponValues;
import com.paysi.catalog.coupon.port.CouponRepository;
import com.paysi.catalog.offer.port.OfferRepository;
import com.paysi.core.error.NotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class CouponService {
    private final CouponRepository coupons;
    private final OfferRepository offers;
    private final Clock clock;

    @Autowired
    public CouponService(CouponRepository coupons, OfferRepository offers) {
        this(coupons, offers, Clock.systemUTC());
    }

    CouponService(CouponRepository coupons, OfferRepository offers, Clock clock) {
        this.coupons = coupons;
        this.offers = offers;
        this.clock = clock;
    }

    @Transactional
    public Coupon create(UUID sellerId, String code, CouponValues values) {
        requireOwnedOffers(sellerId, values);
        Coupon coupon = Coupon.create(UUID.randomUUID(), sellerId, normalize(code), values, clock.instant());
        coupons.insert(coupon);
        return coupon;
    }

    @Transactional(readOnly = true)
    public List<Coupon> list(UUID sellerId) {
        return coupons.listActiveOwned(sellerId);
    }

    @Transactional(readOnly = true)
    public Coupon get(UUID sellerId, UUID couponId) {
        return requireCoupon(sellerId, couponId);
    }

    @Transactional
    public Coupon update(UUID sellerId, UUID couponId, CouponValues values) {
        requireOwnedOffers(sellerId, values);
        Coupon changed = requireCoupon(sellerId, couponId).update(values);
        coupons.update(changed);
        return changed;
    }

    @Transactional
    public void archive(UUID sellerId, UUID couponId) {
        requireCoupon(sellerId, couponId);
        if (!coupons.archive(sellerId, couponId, clock.instant())) throw couponNotFound();
    }

    private void requireOwnedOffers(UUID sellerId, CouponValues values) {
        for (UUID offerId : values.offerIds()) {
            offers.findActiveOwned(sellerId, offerId).orElseThrow(CouponService::offerNotFound);
        }
    }

    private Coupon requireCoupon(UUID sellerId, UUID couponId) {
        return coupons.findActiveOwned(sellerId, couponId).orElseThrow(CouponService::couponNotFound);
    }

    private static String normalize(String code) {
        return code == null ? null : code.trim().toUpperCase(Locale.ROOT);
    }

    private static NotFoundException offerNotFound() {
        return new NotFoundException("OFFER_NOT_FOUND", "Oferta não encontrada");
    }

    private static NotFoundException couponNotFound() {
        return new NotFoundException("COUPON_NOT_FOUND", "Cupom não encontrado");
    }
}
