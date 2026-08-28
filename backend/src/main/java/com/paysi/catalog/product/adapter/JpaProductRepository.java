package com.paysi.catalog.product.adapter;

import com.paysi.catalog.product.app.ProductCursor;
import com.paysi.catalog.product.domain.Product;
import com.paysi.catalog.product.port.ProductRepository;
import com.paysi.core.error.ConflictException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class JpaProductRepository implements ProductRepository {
    private final ProductJpaRepository jpa;

    JpaProductRepository(ProductJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public void insert(Product product) {
        jpa.saveAndFlush(ProductEntity.from(product));
    }

    @Override
    public Optional<Product> findActiveOwned(UUID sellerId, UUID productId) {
        return jpa.findByIdAndSellerIdAndArchivedAtIsNull(productId, sellerId)
                .map(ProductEntity::toDomain);
    }

    @Override
    public List<Product> listActiveOwned(UUID sellerId, ProductCursor cursor, int limit) {
        List<ProductEntity> rows = cursor == null
                ? jpa.firstPage(sellerId, limit)
                : jpa.pageAfter(sellerId, cursor.createdAt(), cursor.id(), limit);
        return rows.stream().map(ProductEntity::toDomain).toList();
    }

    @Override
    public boolean hasOffers(UUID productId) {
        return jpa.hasOffers(productId);
    }

    @Override
    public void update(Product product) {
        ProductEntity entity = jpa.findByIdAndSellerIdAndArchivedAtIsNull(product.id(), product.sellerId())
                .orElseThrow(() -> new IllegalStateException("Produto desapareceu durante a atualização"));
        entity.apply(product);
        try {
            jpa.saveAndFlush(entity);
        } catch (DataIntegrityViolationException error) {
            if (containsMessage(error, "segment e charge_type imutaveis apos existir oferta")) {
                throw new ConflictException("PRODUCT_CONTRACT_IMMUTABLE",
                        "Segmento e tipo de cobrança não podem mudar após a criação de uma oferta", null);
            }
            throw error;
        }
    }

    @Override
    public boolean archive(UUID sellerId, UUID productId, Instant archivedAt) {
        return jpa.archive(sellerId, productId, archivedAt) == 1;
    }

    private static boolean containsMessage(Throwable error, String expected) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (String.valueOf(cause.getMessage()).contains(expected)) return true;
        }
        return false;
    }
}
