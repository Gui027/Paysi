package com.paysi.catalog.offer.app;

import com.paysi.catalog.offer.domain.Offer;
import com.paysi.catalog.offer.domain.OfferValues;
import com.paysi.catalog.offer.port.OfferRepository;
import com.paysi.catalog.product.domain.Product;
import com.paysi.catalog.product.port.ProductRepository;
import com.paysi.core.error.NotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class OfferService {
    private final OfferRepository offers;
    private final ProductRepository products;
    private final Clock clock;

    @Autowired
    public OfferService(OfferRepository offers, ProductRepository products) {
        this(offers, products, Clock.systemUTC());
    }

    OfferService(OfferRepository offers, ProductRepository products, Clock clock) {
        this.offers = offers;
        this.products = products;
        this.clock = clock;
    }

    @Transactional
    public OfferView create(UUID sellerId, UUID productId, OfferValues values) {
        Product product = requireProduct(sellerId, productId);
        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        Offer offer = Offer.create(id, productId, product.chargeType(), product.segment(),
                slug(product.name(), id), values, now);
        offers.insert(offer);
        return view(offer, now);
    }

    @Transactional(readOnly = true)
    public List<OfferView> list(UUID sellerId, UUID productId) {
        requireProduct(sellerId, productId);
        Instant now = clock.instant();
        return offers.listActiveOwned(sellerId, productId).stream().map(offer -> view(offer, now)).toList();
    }

    @Transactional(readOnly = true)
    public OfferView get(UUID sellerId, UUID offerId) {
        return view(requireOffer(sellerId, offerId), clock.instant());
    }

    @Transactional
    public OfferView update(UUID sellerId, UUID offerId, OfferValues values) {
        Offer changed = requireOffer(sellerId, offerId).update(values, clock.instant());
        offers.update(changed);
        return view(changed, clock.instant());
    }

    @Transactional
    public void archive(UUID sellerId, UUID offerId) {
        requireOffer(sellerId, offerId);
        if (!offers.archive(sellerId, offerId, clock.instant())) throw offerNotFound();
    }

    private Product requireProduct(UUID sellerId, UUID productId) {
        return products.findActiveOwned(sellerId, productId).orElseThrow(OfferService::productNotFound);
    }

    private Offer requireOffer(UUID sellerId, UUID offerId) {
        return offers.findActiveOwned(sellerId, offerId).orElseThrow(OfferService::offerNotFound);
    }

    private static OfferView view(Offer offer, Instant now) {
        return new OfferView(offer, now.plus(offer.payoutDelay().days(), ChronoUnit.DAYS));
    }

    private static String slug(String productName, UUID id) {
        String base = Normalizer.normalize(productName, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        if (base.isBlank()) base = "oferta";
        return base + "-" + id.toString().substring(0, 8);
    }

    private static NotFoundException productNotFound() {
        return new NotFoundException("PRODUCT_NOT_FOUND", "Produto não encontrado");
    }

    private static NotFoundException offerNotFound() {
        return new NotFoundException("OFFER_NOT_FOUND", "Oferta não encontrada");
    }
}
