package com.paysi.checkout.pub.app;

import com.paysi.catalog.appearance.domain.Appearance;
import com.paysi.catalog.appearance.port.AppearanceRepository;
import com.paysi.catalog.asset.port.AssetRepository;
import com.paysi.catalog.offer.domain.BillingCycle;
import com.paysi.catalog.offer.domain.Offer;
import com.paysi.catalog.offer.port.OfferRepository;
import com.paysi.catalog.product.domain.ChargeType;
import com.paysi.catalog.product.domain.Segment;
import com.paysi.checkout.pub.port.ProductNameLookup;
import com.paysi.core.error.NotFoundException;
import com.paysi.identity.domain.PersonType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class CheckoutContractService {
    private static final List<String> BASE_FIELDS = List.of("name", "email", "personType", "taxId");
    private static final List<String> COMPANY_FIELDS = List.of("legalName", "municipalReg",
            "address.zipCode", "address.street", "address.number", "address.complement",
            "address.district", "address.city", "address.state");

    private final OfferRepository offers;
    private final AppearanceRepository appearances;
    private final AssetRepository assets;
    private final ProductNameLookup products;
    private final Clock clock;
    private final String assetBaseUrl;
    private final String termsUrl;
    private final String privacyUrl;

    @Autowired
    public CheckoutContractService(OfferRepository offers, AppearanceRepository appearances,
            AssetRepository assets, ProductNameLookup products,
            @Value("${paysi.asset.public-base-url:http://localhost:8080}") String assetBaseUrl,
            @Value("${paysi.checkout.legal.terms-url:https://paysi.com.br/termos}") String termsUrl,
            @Value("${paysi.checkout.legal.privacy-url:https://paysi.com.br/privacidade}") String privacyUrl) {
        this(offers, appearances, assets, products, Clock.systemUTC(), assetBaseUrl, termsUrl, privacyUrl);
    }

    CheckoutContractService(OfferRepository offers, AppearanceRepository appearances,
            AssetRepository assets, ProductNameLookup products, Clock clock,
            String assetBaseUrl, String termsUrl, String privacyUrl) {
        this.offers = offers;
        this.appearances = appearances;
        this.assets = assets;
        this.products = products;
        this.clock = clock;
        this.assetBaseUrl = assetBaseUrl.replaceAll("/+$", "");
        this.termsUrl = termsUrl;
        this.privacyUrl = privacyUrl;
    }

    @Transactional(readOnly = true)
    public CheckoutContract get(String slug) {
        Offer offer = offers.findPublishedBySlug(slug).orElseThrow(CheckoutContractService::notFound);
        String product = products.findActiveProductName(offer.productId())
                .orElseThrow(CheckoutContractService::notFound);
        Appearance appearance = appearances.findByOfferId(offer.id())
                .orElseGet(() -> Appearance.defaults(offer.id(), clock.instant()));
        Instant now = clock.instant();
        Instant nextChargeAt = offer.chargeType() == ChargeType.SUBSCRIPTION
                ? nextCharge(now, offer.cycle()) : null;

        return new CheckoutContract(product, offer.segment(), offer.chargeType(), offer.priceCents(),
                offer.cycle(), now, nextChargeAt, offer.paymentMethods(), offer.maxInstallments(),
                requiredBuyerFields(offer.segment()), appearanceContract(appearance),
                new CheckoutContract.LegalTexts(termsUrl, privacyUrl));
    }

    private CheckoutContract.Appearance appearanceContract(Appearance appearance) {
        return new CheckoutContract.Appearance(assetUrl(appearance.logoAssetId()),
                assetUrl(appearance.bannerAssetId()), assetUrl(appearance.sideImageAssetId()),
                appearance.primaryColor(), appearance.buttonText());
    }

    private String assetUrl(UUID assetId) {
        if (assetId == null) return null;
        return assets.findActive(assetId)
                .map(asset -> assetBaseUrl + "/v1/assets/" + asset.id() + "/content")
                .orElse(null);
    }

    private static Map<PersonType, List<String>> requiredBuyerFields(Segment segment) {
        List<String> pf = segment == Segment.SAAS ? merge(BASE_FIELDS, COMPANY_FIELDS) : BASE_FIELDS;
        List<String> pj = merge(BASE_FIELDS, COMPANY_FIELDS);
        return Map.of(PersonType.PF, pf, PersonType.PJ, pj);
    }

    private static List<String> merge(List<String> first, List<String> second) {
        List<String> merged = new ArrayList<>(first);
        merged.addAll(second);
        return List.copyOf(merged);
    }

    private static Instant nextCharge(Instant now, BillingCycle cycle) {
        int months = switch (cycle) {
            case MONTHLY -> 1;
            case QUARTERLY -> 3;
            case SEMIANNUAL -> 6;
            case ANNUAL -> 12;
        };
        return ZonedDateTime.ofInstant(now, ZoneOffset.UTC).plusMonths(months).toInstant();
    }

    private static NotFoundException notFound() {
        return new NotFoundException("OFFER_NOT_FOUND", "Oferta não encontrada");
    }
}
