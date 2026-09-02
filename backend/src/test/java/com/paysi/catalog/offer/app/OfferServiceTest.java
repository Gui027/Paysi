package com.paysi.catalog.offer.app;

import com.paysi.catalog.offer.domain.BillingCycle;
import com.paysi.catalog.offer.domain.Offer;
import com.paysi.catalog.offer.domain.OfferPaymentMethod;
import com.paysi.catalog.offer.domain.OfferPayoutDelay;
import com.paysi.catalog.offer.domain.OfferValues;
import com.paysi.catalog.offer.port.OfferRepository;
import com.paysi.catalog.product.app.ProductCursor;
import com.paysi.catalog.product.domain.ChargeType;
import com.paysi.catalog.product.domain.Product;
import com.paysi.catalog.product.domain.Segment;
import com.paysi.catalog.product.port.ProductRepository;
import com.paysi.core.error.NotFoundException;
import com.paysi.core.error.ValidationException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OfferServiceTest {
    private static final UUID SELLER = UUID.randomUUID();
    private static final UUID OTHER_SELLER = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-09-02T12:00:00Z");

    @Test
    void createsDraftWithProductContractPermanentSlugAndServerAvailability() {
        var products = new InMemoryProducts(product(SELLER, "Gestão Ágil", Segment.SAAS,
                ChargeType.SUBSCRIPTION));
        var offers = new InMemoryOffers();
        var service = service(offers, products);

        OfferView created = service.create(SELLER, products.products.getFirst().id(), subscription());

        assertThat(created.offer().slug()).matches("gestao-agil-[0-9a-f]{8}");
        assertThat(created.offer().segment()).isEqualTo(Segment.SAAS);
        assertThat(created.offer().chargeType()).isEqualTo(ChargeType.SUBSCRIPTION);
        assertThat(created.offer().paymentMethods()).containsExactlyInAnyOrder(
                OfferPaymentMethod.PIX, OfferPaymentMethod.CARD, OfferPaymentMethod.BOLETO);
        assertThat(created.availableAt()).isEqualTo(NOW.plusSeconds(15L * 86_400));

        String permanentSlug = created.offer().slug();
        OfferView updated = service.update(SELLER, created.offer().id(), new OfferValues(
                15_000, BillingCycle.ANNUAL, 0, true, 14, 6, 5, 7,
                Set.of(OfferPaymentMethod.CARD), OfferPayoutDelay.D7));
        assertThat(updated.offer().slug()).isEqualTo(permanentSlug);
        assertThat(updated.availableAt()).isEqualTo(NOW.plusSeconds(7L * 86_400));
    }

    @Test
    void validatesEveryCommercialCombination() {
        assertInvalid(oneTime(1_999, null, true, Set.of(OfferPaymentMethod.CARD)), "priceCents");
        assertInvalid(oneTime(2_000, BillingCycle.MONTHLY, true, Set.of(OfferPaymentMethod.CARD)), "cycle");
        assertInvalid(values(2_000, null, 31, true, 7, 1, 3, 5,
                Set.of(OfferPaymentMethod.CARD)), "trialDays");
        assertInvalid(oneTime(2_000, null, false, Set.of(OfferPaymentMethod.CARD)), "trialRequiresCard");
        assertInvalid(values(2_000, null, 0, true, 6, 1, 3, 5,
                Set.of(OfferPaymentMethod.CARD)), "guaranteeDays");
        assertInvalid(values(2_000, null, 0, true, 7, 13, 3, 5,
                Set.of(OfferPaymentMethod.CARD)), "maxInstallments");
        assertInvalid(values(2_000, null, 0, true, 7, 1, 16, 5,
                Set.of(OfferPaymentMethod.CARD)), "boletoDueDays");
        assertInvalid(values(2_000, null, 0, true, 7, 1, 3, 2,
                Set.of(OfferPaymentMethod.CARD)), "boletoAdvanceDays");
        assertInvalid(oneTime(2_000, null, true, Set.of()), "paymentMethods");
        assertInvalid(oneTime(2_000, null, true, Set.of(OfferPaymentMethod.BOLETO)), "paymentMethods");

        Product subscription = product(SELLER, "SaaS", Segment.SAAS, ChargeType.SUBSCRIPTION);
        assertThatThrownBy(() -> service(new InMemoryOffers(), new InMemoryProducts(subscription))
                .create(SELLER, subscription.id(), values(2_000, null, 0, true, 7, 1, 3, 5,
                        Set.of(OfferPaymentMethod.CARD))))
                .isInstanceOfSatisfying(ValidationException.class,
                        error -> assertThat(error.field()).isEqualTo("cycle"));
    }

    @Test
    void isolatesOffersBetweenSellerAccountsAndArchivesLogically() {
        Product foreign = product(OTHER_SELLER, "Alheio", Segment.DIGITAL, ChargeType.ONE_TIME);
        var products = new InMemoryProducts(foreign);
        var offers = new InMemoryOffers();
        var service = service(offers, products);

        assertThatThrownBy(() -> service.create(SELLER, foreign.id(), oneTime()))
                .isInstanceOfSatisfying(NotFoundException.class,
                        error -> assertThat(error.code()).isEqualTo("PRODUCT_NOT_FOUND"));

        Offer stored = Offer.create(UUID.randomUUID(), foreign.id(), ChargeType.ONE_TIME,
                Segment.DIGITAL, "alheio-12345678", oneTime(), NOW);
        offers.rows.add(stored);
        offers.owners.put(stored.id(), OTHER_SELLER);
        assertThatThrownBy(() -> service.get(SELLER, stored.id()))
                .isInstanceOfSatisfying(NotFoundException.class,
                        error -> assertThat(error.code()).isEqualTo("OFFER_NOT_FOUND"));

        service.archive(OTHER_SELLER, stored.id());
        assertThatThrownBy(() -> service.get(OTHER_SELLER, stored.id()))
                .isInstanceOf(NotFoundException.class);
    }

    private static void assertInvalid(OfferValues values, String field) {
        Product product = product(SELLER, "Digital", Segment.DIGITAL, ChargeType.ONE_TIME);
        assertThatThrownBy(() -> service(new InMemoryOffers(), new InMemoryProducts(product))
                .create(SELLER, product.id(), values))
                .isInstanceOfSatisfying(ValidationException.class,
                        error -> assertThat(error.field()).isEqualTo(field));
    }

    private static OfferService service(InMemoryOffers offers, InMemoryProducts products) {
        return new OfferService(offers, products, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static OfferValues subscription() {
        return values(10_000, BillingCycle.MONTHLY, 7, false, 7, 12, 3, 5,
                Set.of(OfferPaymentMethod.PIX, OfferPaymentMethod.CARD, OfferPaymentMethod.BOLETO),
                OfferPayoutDelay.D15);
    }

    private static OfferValues oneTime() {
        return oneTime(10_000, null, true, Set.of(OfferPaymentMethod.PIX, OfferPaymentMethod.CARD));
    }

    private static OfferValues oneTime(long price, BillingCycle cycle, boolean card,
                                       Set<OfferPaymentMethod> methods) {
        return values(price, cycle, 0, card, 7, 1, 3, 5, methods);
    }

    private static OfferValues values(long price, BillingCycle cycle, int trial, boolean card,
                                      int guarantee, int installments, int due, int advance,
                                      Set<OfferPaymentMethod> methods) {
        return values(price, cycle, trial, card, guarantee, installments, due, advance, methods,
                OfferPayoutDelay.D32);
    }

    private static OfferValues values(long price, BillingCycle cycle, int trial, boolean card,
                                      int guarantee, int installments, int due, int advance,
                                      Set<OfferPaymentMethod> methods, OfferPayoutDelay payout) {
        return new OfferValues(price, cycle, trial, card, guarantee, installments, due, advance,
                methods, payout);
    }

    private static Product product(UUID seller, String name, Segment segment, ChargeType chargeType) {
        return Product.createDraft(UUID.randomUUID(), seller, name, null, segment, chargeType, false, NOW);
    }

    private static final class InMemoryProducts implements ProductRepository {
        private final List<Product> products = new ArrayList<>();

        private InMemoryProducts(Product... products) {
            this.products.addAll(List.of(products));
        }

        public void insert(Product product) { products.add(product); }
        public Optional<Product> findActiveOwned(UUID sellerId, UUID productId) {
            return products.stream().filter(p -> p.id().equals(productId) && p.sellerId().equals(sellerId)
                    && p.archivedAt() == null).findFirst();
        }
        public List<Product> listActiveOwned(UUID sellerId, ProductCursor cursor, int limit) { return List.of(); }
        public boolean hasOffers(UUID productId) { return false; }
        public void update(Product product) { }
        public boolean archive(UUID sellerId, UUID productId, Instant archivedAt) { return false; }
    }

    private static final class InMemoryOffers implements OfferRepository {
        private final List<Offer> rows = new ArrayList<>();
        private final Map<UUID, UUID> owners = new HashMap<>();
        public void insert(Offer offer) { rows.add(offer); owners.put(offer.id(), SELLER); }
        public List<Offer> listActiveOwned(UUID sellerId, UUID productId) {
            return rows.stream().filter(o -> o.productId().equals(productId) && o.archivedAt() == null).toList();
        }
        public Optional<Offer> findActiveOwned(UUID sellerId, UUID offerId) {
            return rows.stream().filter(o -> o.id().equals(offerId) && o.archivedAt() == null)
                    .filter(o -> owners.get(o.id()).equals(sellerId)).findFirst();
        }
        public void update(Offer offer) { rows.replaceAll(o -> o.id().equals(offer.id()) ? offer : o); }
        public boolean archive(UUID sellerId, UUID offerId, Instant archivedAt) {
            Optional<Offer> found = findActiveOwned(sellerId, offerId);
            if (found.isEmpty()) return false;
            rows.remove(found.get());
            return true;
        }
    }
}
