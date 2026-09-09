package com.paysi.catalog.coupon.app;

import com.paysi.catalog.coupon.domain.Coupon;
import com.paysi.catalog.coupon.domain.CouponKind;
import com.paysi.catalog.coupon.domain.CouponValues;
import com.paysi.catalog.coupon.port.CouponRepository;
import com.paysi.catalog.offer.domain.BillingCycle;
import com.paysi.catalog.offer.domain.Offer;
import com.paysi.catalog.offer.domain.OfferPaymentMethod;
import com.paysi.catalog.offer.domain.OfferPayoutDelay;
import com.paysi.catalog.offer.domain.OfferStatus;
import com.paysi.catalog.offer.port.OfferRepository;
import com.paysi.catalog.product.domain.ChargeType;
import com.paysi.catalog.product.domain.Segment;
import com.paysi.core.error.NotFoundException;
import com.paysi.core.error.ValidationException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CouponServiceTest {
    private static final UUID SELLER = UUID.randomUUID();
    private static final UUID OTHER_SELLER = UUID.randomUUID();
    private static final UUID OFFER = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-09-02T12:00:00Z");

    @Test
    void createsCouponWithNormalizedCodeAndOwnedOffers() {
        var offers = new InMemoryOffers(Map.of(OFFER, SELLER));
        var coupons = new InMemoryCoupons();
        var service = service(coupons, offers);

        Coupon created = service.create(SELLER, " promo10 ", percent(1_000, Set.of(OFFER)));

        assertThat(created.code()).isEqualTo("PROMO10");
        assertThat(created.kind()).isEqualTo(CouponKind.PERCENT);
        assertThat(created.value()).isEqualTo(1_000);
        assertThat(created.redeemedCount()).isZero();
        assertThat(created.offerIds()).containsExactly(OFFER);
    }

    @Test
    void rejectsCouponReferencingOfferFromAnotherSeller() {
        var offers = new InMemoryOffers(Map.of(OFFER, OTHER_SELLER));
        var service = service(new InMemoryCoupons(), offers);

        assertThatThrownBy(() -> service.create(SELLER, "PROMO10", percent(1_000, Set.of(OFFER))))
                .isInstanceOfSatisfying(NotFoundException.class,
                        error -> assertThat(error.code()).isEqualTo("OFFER_NOT_FOUND"));
    }

    @Test
    void validatesDiscountRangesAndRequiredFields() {
        assertInvalid(percent(0, Set.of(OFFER)), "value");
        assertInvalid(percent(10_001, Set.of(OFFER)), "value");
        assertInvalid(fixed(0, Set.of(OFFER)), "value");
        assertInvalid(new CouponValues(CouponKind.PERCENT, 1_000, NOW, NOW.minusSeconds(60),
                null, 1, Set.of(OFFER)), "expiresAt");
        assertInvalid(new CouponValues(CouponKind.PERCENT, 1_000, null, null, 0, 1, Set.of(OFFER)),
                "maxRedemptions");
        assertInvalid(new CouponValues(CouponKind.PERCENT, 1_000, null, null, null, 0, Set.of(OFFER)),
                "maxPerBuyer");
        assertInvalid(percent(1_000, Set.of()), "offerIds");
    }

    @Test
    void isolatesCouponsBetweenSellersAndArchivingPreservesRedemptionHistory() {
        var offers = new InMemoryOffers(Map.of(OFFER, SELLER));
        var coupons = new InMemoryCoupons();
        var service = service(coupons, offers);

        Coupon created = service.create(SELLER, "PROMO10", percent(1_000, Set.of(OFFER)));

        assertThatThrownBy(() -> service.get(OTHER_SELLER, created.id()))
                .isInstanceOfSatisfying(NotFoundException.class,
                        error -> assertThat(error.code()).isEqualTo("COUPON_NOT_FOUND"));

        service.archive(SELLER, created.id());
        assertThatThrownBy(() -> service.get(SELLER, created.id())).isInstanceOf(NotFoundException.class);
        assertThat(coupons.rows).extracting(Coupon::id).contains(created.id());
    }

    private static void assertInvalid(CouponValues values, String field) {
        var offers = new InMemoryOffers(Map.of(OFFER, SELLER));
        assertThatThrownBy(() -> service(new InMemoryCoupons(), offers).create(SELLER, "PROMO10", values))
                .isInstanceOfSatisfying(ValidationException.class,
                        error -> assertThat(error.field()).isEqualTo(field));
    }

    private static CouponService service(InMemoryCoupons coupons, InMemoryOffers offers) {
        return new CouponService(coupons, offers, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static CouponValues percent(int bps, Set<UUID> offerIds) {
        return new CouponValues(CouponKind.PERCENT, bps, null, null, null, 1, offerIds);
    }

    private static CouponValues fixed(int cents, Set<UUID> offerIds) {
        return new CouponValues(CouponKind.FIXED, cents, null, null, null, 1, offerIds);
    }

    private static final class InMemoryOffers implements OfferRepository {
        private final Map<UUID, UUID> ownerByOffer;

        private InMemoryOffers(Map<UUID, UUID> ownerByOffer) {
            this.ownerByOffer = ownerByOffer;
        }

        public void insert(Offer offer) { }
        public List<Offer> listActiveOwned(UUID sellerId, UUID productId) { return List.of(); }
        public Optional<Offer> findActiveOwned(UUID sellerId, UUID offerId) {
            if (!sellerId.equals(ownerByOffer.get(offerId))) return Optional.empty();
            return Optional.of(new Offer(offerId, UUID.randomUUID(), ChargeType.ONE_TIME, Segment.DIGITAL,
                    "slug-12345678", 10_000, null, 0, true, 7, 1, 3, 5,
                    Set.of(OfferPaymentMethod.PIX), OfferPayoutDelay.D7, OfferStatus.DRAFT, null, NOW, NOW));
        }
        public Optional<Offer> findPublishedBySlug(String slug) { return Optional.empty(); }
        public void update(Offer offer) { }
        public boolean publish(UUID sellerId, UUID offerId, Instant publishedAt) { return false; }
        public boolean archive(UUID sellerId, UUID offerId, Instant archivedAt) { return false; }
    }

    private static final class InMemoryCoupons implements CouponRepository {
        private final List<Coupon> rows = new ArrayList<>();
        private final Map<UUID, UUID> owners = new HashMap<>();
        public void insert(Coupon coupon) { rows.add(coupon); owners.put(coupon.id(), coupon.sellerId()); }
        public List<Coupon> listActiveOwned(UUID sellerId) {
            return rows.stream().filter(c -> c.sellerId().equals(sellerId) && c.archivedAt() == null).toList();
        }
        public Optional<Coupon> findActiveOwned(UUID sellerId, UUID couponId) {
            return rows.stream().filter(c -> c.id().equals(couponId) && c.archivedAt() == null)
                    .filter(c -> c.sellerId().equals(sellerId)).findFirst();
        }
        public void update(Coupon coupon) { rows.replaceAll(c -> c.id().equals(coupon.id()) ? coupon : c); }
        public boolean archive(UUID sellerId, UUID couponId, Instant archivedAt) {
            for (int i = 0; i < rows.size(); i++) {
                Coupon c = rows.get(i);
                if (c.id().equals(couponId) && c.sellerId().equals(sellerId) && c.archivedAt() == null) {
                    rows.set(i, new Coupon(c.id(), c.sellerId(), c.code(), c.kind(), c.value(),
                            c.startsAt(), c.expiresAt(), c.maxRedemptions(), c.maxPerBuyer(),
                            c.redeemedCount(), c.offerIds(), archivedAt, c.createdAt()));
                    return true;
                }
            }
            return false;
        }
    }
}
