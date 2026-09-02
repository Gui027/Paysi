package com.paysi.catalog.offer.app;

import com.paysi.catalog.offer.domain.Offer;
import com.paysi.catalog.offer.domain.OfferStatus;
import com.paysi.catalog.offer.port.OfferRepository;
import com.paysi.catalog.offer.port.PublicationRequirementRepository;
import com.paysi.catalog.product.domain.Segment;
import com.paysi.core.error.ConflictException;
import com.paysi.core.error.NotFoundException;
import com.paysi.identity.domain.KycStatus;
import com.paysi.identity.kyc.app.KycService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
public class OfferPublicationService {
    private final OfferRepository offers;
    private final PublicationRequirementRepository requirements;
    private final KycService kyc;
    private final Clock clock;

    @Autowired
    public OfferPublicationService(OfferRepository offers, PublicationRequirementRepository requirements,
                                   KycService kyc) {
        this(offers, requirements, kyc, Clock.systemUTC());
    }

    OfferPublicationService(OfferRepository offers, PublicationRequirementRepository requirements,
                            KycService kyc, Clock clock) {
        this.offers = offers;
        this.requirements = requirements;
        this.kyc = kyc;
        this.clock = clock;
    }

    @Transactional
    public OfferPublication publish(UUID sellerId, UUID offerId) {
        Offer offer = requireOwned(sellerId, offerId);
        if (offer.status() == OfferStatus.PUBLISHED) return OfferPublication.published(view(offer));

        var currentKyc = kyc.current(sellerId);
        if (currentKyc.kycStatus() != KycStatus.APPROVED) {
            var action = kyc.start(sellerId);
            return OfferPublication.action(PublicationAction.COMPLETE_KYC, action.providerUrl(), view(offer));
        }

        // RF-092/PRE-11: DIGITAL requires automated fiscal readiness; SaaS may issue externally.
        if (offer.segment() == Segment.DIGITAL && !requirements.hasValidatedFiscalProfile(sellerId)) {
            return OfferPublication.action(PublicationAction.CONFIGURE_FISCAL,
                    "/v1/fiscal-profile", view(offer));
        }

        Instant now = clock.instant();
        Offer published = offer.publish(now);
        if (!offers.publish(sellerId, offerId, now)) {
            Offer concurrent = requireOwned(sellerId, offerId);
            if (concurrent.status() != OfferStatus.PUBLISHED) {
                throw new ConflictException("OFFER_PUBLICATION_CONFLICT",
                        "A oferta mudou durante a publicação", null);
            }
            published = concurrent;
        }
        return OfferPublication.published(view(published));
    }

    private Offer requireOwned(UUID sellerId, UUID offerId) {
        return offers.findActiveOwned(sellerId, offerId).orElseThrow(() ->
                new NotFoundException("OFFER_NOT_FOUND", "Oferta não encontrada"));
    }

    private OfferView view(Offer offer) {
        return new OfferView(offer, clock.instant().plus(offer.payoutDelay().days(), ChronoUnit.DAYS));
    }
}
