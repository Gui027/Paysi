package com.paysi.checkout.pub.app;

import com.paysi.catalog.appearance.domain.Appearance;
import com.paysi.catalog.appearance.port.AppearanceRepository;
import com.paysi.catalog.asset.domain.Asset;
import com.paysi.catalog.asset.domain.AssetKind;
import com.paysi.catalog.asset.port.AssetRepository;
import com.paysi.catalog.offer.domain.BillingCycle;
import com.paysi.catalog.offer.domain.Offer;
import com.paysi.catalog.offer.domain.OfferPaymentMethod;
import com.paysi.catalog.offer.domain.OfferPayoutDelay;
import com.paysi.catalog.offer.domain.OfferStatus;
import com.paysi.catalog.offer.port.OfferRepository;
import com.paysi.catalog.product.domain.ChargeType;
import com.paysi.catalog.product.domain.Segment;
import com.paysi.checkout.pub.port.ProductNameLookup;
import com.paysi.core.error.NotFoundException;
import com.paysi.identity.domain.PersonType;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CheckoutContractServiceTest {
    private static final UUID PRODUCT = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-09-02T12:00:00Z");

    @Test
    void oneTimeDigitalOfferOmitsCycleAndNextChargeAndUsesBaseBuyerFields() {
        var service = service(offer("digital-oneshot", ChargeType.ONE_TIME, Segment.DIGITAL, null),
                Map.of(), Map.of());

        CheckoutContract contract = service.get("digital-oneshot");

        assertThat(contract.cycle()).isNull();
        assertThat(contract.nextChargeAt()).isNull();
        assertThat(contract.requiredBuyerFields().get(PersonType.PF)).containsExactly(
                "name", "email", "personType", "taxId");
        assertThat(contract.requiredBuyerFields().get(PersonType.PJ)).contains("legalName", "municipalReg");
    }

    @Test
    void subscriptionDigitalOfferComputesNextChargeFromCycleAndKeepsPfSimple() {
        var service = service(offer("digital-sub", ChargeType.SUBSCRIPTION, Segment.DIGITAL,
                BillingCycle.MONTHLY), Map.of(), Map.of());

        CheckoutContract contract = service.get("digital-sub");

        assertThat(contract.nextChargeAt()).isEqualTo(Instant.parse("2026-10-02T12:00:00Z"));
        assertThat(contract.requiredBuyerFields().get(PersonType.PF)).doesNotContain("legalName");
    }

    @Test
    void oneTimeSaasOfferRequiresCompanyFieldsEvenForIndividuals() {
        var service = service(offer("saas-oneshot", ChargeType.ONE_TIME, Segment.SAAS, null),
                Map.of(), Map.of());

        CheckoutContract contract = service.get("saas-oneshot");

        assertThat(contract.requiredBuyerFields().get(PersonType.PF)).contains("legalName", "municipalReg");
        assertThat(contract.requiredBuyerFields().get(PersonType.PJ)).contains("legalName", "municipalReg");
    }

    @Test
    void subscriptionSaasOfferComputesAnnualNextChargeAndResolvesAppearanceUrls() {
        UUID offerId = UUID.randomUUID();
        Offer offer = offer(offerId, "saas-sub", ChargeType.SUBSCRIPTION, Segment.SAAS, BillingCycle.ANNUAL);
        UUID logo = UUID.randomUUID();
        var appearances = Map.of(offerId, new Appearance(offerId, logo, null, null, "#000000", "Assinar", NOW));
        var assets = Map.of(logo, new Asset(logo, UUID.randomUUID(), AssetKind.LOGO, "k.png", "image/png",
                10, 1, 1, null, NOW));
        var service = service(offer, appearances, assets);

        CheckoutContract contract = service.get("saas-sub");

        assertThat(contract.nextChargeAt()).isEqualTo(Instant.parse("2027-09-02T12:00:00Z"));
        assertThat(contract.appearance().logoUrl()).isEqualTo("http://localhost:8080/v1/assets/" + logo + "/content");
        assertThat(contract.appearance().bannerUrl()).isNull();
        assertThat(contract.appearance().primaryColor()).isEqualTo("#000000");
    }

    @Test
    void returnsNotFoundForDraftOrArchivedOffer() {
        var service = service(null, Map.of(), Map.of());
        assertThatThrownBy(() -> service.get("nao-publicada"))
                .isInstanceOfSatisfying(NotFoundException.class,
                        error -> assertThat(error.code()).isEqualTo("OFFER_NOT_FOUND"));
    }

    private static Offer offer(String slug, ChargeType chargeType, Segment segment, BillingCycle cycle) {
        return offer(UUID.randomUUID(), slug, chargeType, segment, cycle);
    }

    private static Offer offer(UUID id, String slug, ChargeType chargeType, Segment segment,
                               BillingCycle cycle) {
        return new Offer(id, PRODUCT, chargeType, segment, slug, 10_000, cycle, 0, true, 7, 1, 3, 5,
                Set.of(OfferPaymentMethod.PIX, OfferPaymentMethod.CARD), OfferPayoutDelay.D7,
                OfferStatus.PUBLISHED, null, NOW, NOW);
    }

    private static CheckoutContractService service(Offer offer, Map<UUID, Appearance> appearances,
                                                    Map<UUID, Asset> assets) {
        return new CheckoutContractService(new InMemoryOffers(offer), new InMemoryAppearances(appearances),
                new InMemoryAssets(assets), productId -> Optional.of("Produto Teste"),
                Clock.fixed(NOW, ZoneOffset.UTC), "http://localhost:8080", "https://paysi.com.br/termos",
                "https://paysi.com.br/privacidade");
    }

    private record InMemoryOffers(Offer offer) implements OfferRepository {
        public void insert(Offer offer) { }
        public java.util.List<Offer> listActiveOwned(UUID sellerId, UUID productId) { return java.util.List.of(); }
        public Optional<Offer> findActiveOwned(UUID sellerId, UUID offerId) { return Optional.empty(); }
        public Optional<Offer> findPublishedBySlug(String slug) {
            return offer != null && offer.slug().equals(slug) ? Optional.of(offer) : Optional.empty();
        }
        public void update(Offer offer) { }
        public boolean publish(UUID sellerId, UUID offerId, Instant publishedAt) { return false; }
        public boolean archive(UUID sellerId, UUID offerId, Instant archivedAt) { return false; }
    }

    private record InMemoryAppearances(Map<UUID, Appearance> byOfferId) implements AppearanceRepository {
        public Optional<Appearance> findByOfferId(UUID offerId) {
            return Optional.ofNullable(byOfferId.get(offerId));
        }
        public void save(Appearance appearance) { }
    }

    private record InMemoryAssets(Map<UUID, Asset> byId) implements AssetRepository {
        public void insert(Asset asset) { }
        public Optional<Asset> findActive(UUID assetId) { return Optional.ofNullable(byId.get(assetId)); }
        public Optional<Asset> findActiveOwned(UUID ownerId, UUID assetId) { return Optional.empty(); }
        public boolean archiveOwned(UUID ownerId, UUID assetId, Instant archivedAt) { return false; }
    }
}
