package com.paysi.catalog.offer.port;

import com.paysi.catalog.offer.domain.Offer;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OfferRepository {
    void insert(Offer offer);

    List<Offer> listActiveOwned(UUID sellerId, UUID productId);

    Optional<Offer> findActiveOwned(UUID sellerId, UUID offerId);

    Optional<Offer> findPublishedBySlug(String slug);

    void update(Offer offer);

    boolean publish(UUID sellerId, UUID offerId, java.time.Instant publishedAt);

    boolean archive(UUID sellerId, UUID offerId, java.time.Instant archivedAt);
}
