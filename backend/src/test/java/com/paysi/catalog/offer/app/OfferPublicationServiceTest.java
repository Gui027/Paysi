package com.paysi.catalog.offer.app;

import com.paysi.catalog.offer.domain.BillingCycle;
import com.paysi.catalog.offer.domain.Offer;
import com.paysi.catalog.offer.domain.OfferPaymentMethod;
import com.paysi.catalog.offer.domain.OfferPayoutDelay;
import com.paysi.catalog.offer.domain.OfferStatus;
import com.paysi.catalog.offer.domain.OfferValues;
import com.paysi.catalog.offer.port.OfferRepository;
import com.paysi.catalog.offer.port.PublicationRequirementRepository;
import com.paysi.catalog.product.domain.ChargeType;
import com.paysi.catalog.product.domain.Segment;
import com.paysi.core.error.NotFoundException;
import com.paysi.identity.domain.KycStatus;
import com.paysi.identity.kyc.app.KycService;
import com.paysi.identity.kyc.app.KycView;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OfferPublicationServiceTest {
    private static final UUID SELLER = UUID.randomUUID();
    private static final UUID OTHER = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-09-02T12:00:00Z");

    @Test
    void startsKycAndReturnsActionWithoutPublishing() {
        var repository = new Offers(SELLER, offer(Segment.SAAS));
        KycService kyc = mock(KycService.class);
        var requirements = mock(PublicationRequirementRepository.class);
        when(kyc.current(SELLER)).thenReturn(kyc(KycStatus.PENDING, null));
        when(kyc.start(SELLER)).thenReturn(kyc(KycStatus.SUBMITTED, "https://kyc/process"));

        OfferPublication result = service(repository, requirements, kyc)
                .publish(SELLER, repository.offer.id());

        assertThat(result.published()).isFalse();
        assertThat(result.requiredAction()).isEqualTo(PublicationAction.COMPLETE_KYC);
        assertThat(result.actionUrl()).isEqualTo("https://kyc/process");
        assertThat(repository.offer.status()).isEqualTo(OfferStatus.DRAFT);
        verifyNoInteractions(requirements);
    }

    @Test
    void publishesSaasAfterApprovedKycAndIsIdempotent() {
        var repository = new Offers(SELLER, offer(Segment.SAAS));
        KycService kyc = approvedKyc();
        var requirements = mock(PublicationRequirementRepository.class);
        var service = service(repository, requirements, kyc);

        OfferPublication first = service.publish(SELLER, repository.offer.id());
        OfferPublication repeated = service.publish(SELLER, repository.offer.id());

        assertThat(first.published()).isTrue();
        assertThat(first.offer().offer().status()).isEqualTo(OfferStatus.PUBLISHED);
        assertThat(repeated.published()).isTrue();
        assertThat(repository.publishCount).isEqualTo(1);
        verifyNoInteractions(requirements);
    }

    @Test
    void requiresValidatedFiscalProfileForDigitalOffer() {
        var repository = new Offers(SELLER, offer(Segment.DIGITAL));
        KycService kyc = approvedKyc();
        var requirements = mock(PublicationRequirementRepository.class);
        when(requirements.hasValidatedFiscalProfile(SELLER)).thenReturn(false);

        OfferPublication result = service(repository, requirements, kyc)
                .publish(SELLER, repository.offer.id());

        assertThat(result.published()).isFalse();
        assertThat(result.requiredAction()).isEqualTo(PublicationAction.CONFIGURE_FISCAL);
        assertThat(result.actionUrl()).isEqualTo("/v1/fiscal-profile");
        assertThat(repository.publishCount).isZero();
    }

    @Test
    void publishesDigitalOfferWithValidatedFiscalProfile() {
        var repository = new Offers(SELLER, offer(Segment.DIGITAL));
        var requirements = mock(PublicationRequirementRepository.class);
        when(requirements.hasValidatedFiscalProfile(SELLER)).thenReturn(true);

        OfferPublication result = service(repository, requirements, approvedKyc())
                .publish(SELLER, repository.offer.id());

        assertThat(result.published()).isTrue();
        assertThat(repository.publishCount).isEqualTo(1);
    }

    @Test
    void hidesOfferOwnedByAnotherAccount() {
        var repository = new Offers(OTHER, offer(Segment.SAAS));
        KycService kyc = mock(KycService.class);

        assertThatThrownBy(() -> service(repository, mock(PublicationRequirementRepository.class), kyc)
                .publish(SELLER, repository.offer.id()))
                .isInstanceOfSatisfying(NotFoundException.class,
                        error -> assertThat(error.code()).isEqualTo("OFFER_NOT_FOUND"));
        verify(kyc, never()).current(SELLER);
    }

    private static OfferPublicationService service(Offers repository,
                                                   PublicationRequirementRepository requirements,
                                                   KycService kyc) {
        return new OfferPublicationService(repository, requirements, kyc,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static KycService approvedKyc() {
        KycService kyc = mock(KycService.class);
        when(kyc.current(SELLER)).thenReturn(kyc(KycStatus.APPROVED, null));
        return kyc;
    }

    private static KycView kyc(KycStatus status, String url) {
        return new KycView(SELLER, status, url, List.of());
    }

    private static Offer offer(Segment segment) {
        ChargeType chargeType = segment == Segment.SAAS ? ChargeType.SUBSCRIPTION : ChargeType.ONE_TIME;
        BillingCycle cycle = chargeType == ChargeType.SUBSCRIPTION ? BillingCycle.MONTHLY : null;
        return Offer.create(UUID.randomUUID(), UUID.randomUUID(), chargeType, segment,
                "oferta-12345678", new OfferValues(10_000, cycle, 0, true, 7, 1, 3, 5,
                        Set.of(OfferPaymentMethod.CARD), OfferPayoutDelay.D32), NOW);
    }

    private static final class Offers implements OfferRepository {
        private final UUID owner;
        private Offer offer;
        private int publishCount;

        private Offers(UUID owner, Offer offer) {
            this.owner = owner;
            this.offer = offer;
        }

        public void insert(Offer offer) { this.offer = offer; }
        public List<Offer> listActiveOwned(UUID sellerId, UUID productId) { return List.of(); }
        public Optional<Offer> findActiveOwned(UUID sellerId, UUID offerId) {
            return owner.equals(sellerId) && offer.id().equals(offerId) ? Optional.of(offer) : Optional.empty();
        }
        public Optional<Offer> findPublishedBySlug(String slug) {
            return offer.slug().equals(slug) && offer.status() == OfferStatus.PUBLISHED
                    ? Optional.of(offer) : Optional.empty();
        }
        public void update(Offer offer) { this.offer = offer; }
        public boolean publish(UUID sellerId, UUID offerId, Instant publishedAt) {
            if (!owner.equals(sellerId) || offer.status() != OfferStatus.DRAFT) return false;
            offer = offer.publish(publishedAt);
            publishCount++;
            return true;
        }
        public boolean archive(UUID sellerId, UUID offerId, Instant archivedAt) { return false; }
    }
}
