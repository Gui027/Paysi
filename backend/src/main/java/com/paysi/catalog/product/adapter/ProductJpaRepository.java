package com.paysi.catalog.product.adapter;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface ProductJpaRepository extends JpaRepository<ProductEntity, UUID> {
    Optional<ProductEntity> findByIdAndSellerIdAndArchivedAtIsNull(UUID id, UUID sellerId);

    @Query(value = """
            SELECT * FROM products p
             WHERE p.seller_id = :sellerId
               AND p.archived_at IS NULL
             ORDER BY p.created_at DESC, p.id DESC
             LIMIT :limit
            """, nativeQuery = true)
    List<ProductEntity> firstPage(@Param("sellerId") UUID sellerId, @Param("limit") int limit);

    @Query(value = """
            SELECT * FROM products p
             WHERE p.seller_id = :sellerId
               AND p.archived_at IS NULL
               AND (p.created_at < :createdAt OR (p.created_at = :createdAt AND p.id < :id))
             ORDER BY p.created_at DESC, p.id DESC
             LIMIT :limit
            """, nativeQuery = true)
    List<ProductEntity> pageAfter(@Param("sellerId") UUID sellerId,
                                  @Param("createdAt") Instant createdAt,
                                  @Param("id") UUID id,
                                  @Param("limit") int limit);

    @Query(value = "SELECT EXISTS (SELECT 1 FROM offers o WHERE o.product_id = :productId)",
            nativeQuery = true)
    boolean hasOffers(@Param("productId") UUID productId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE products
               SET archived_at = :archivedAt
             WHERE id = :productId
               AND seller_id = :sellerId
               AND archived_at IS NULL
            """, nativeQuery = true)
    int archive(@Param("sellerId") UUID sellerId, @Param("productId") UUID productId,
                @Param("archivedAt") Instant archivedAt);
}
