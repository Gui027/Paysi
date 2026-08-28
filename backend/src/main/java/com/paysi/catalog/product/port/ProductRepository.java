package com.paysi.catalog.product.port;

import com.paysi.catalog.product.app.ProductCursor;
import com.paysi.catalog.product.domain.Product;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepository {
    void insert(Product product);

    Optional<Product> findActiveOwned(UUID sellerId, UUID productId);

    List<Product> listActiveOwned(UUID sellerId, ProductCursor cursor, int limit);

    boolean hasOffers(UUID productId);

    void update(Product product);

    boolean archive(UUID sellerId, UUID productId, java.time.Instant archivedAt);
}
