package com.paysi.catalog.product.adapter;

import com.paysi.catalog.product.domain.ChargeType;
import com.paysi.catalog.product.domain.Product;
import com.paysi.catalog.product.domain.ProductStatus;
import com.paysi.catalog.product.domain.Segment;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "products")
class ProductEntity {
    @Id
    private UUID id;

    @Column(name = "seller_id", nullable = false, updatable = false)
    private UUID sellerId;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(nullable = false)
    private String segment;

    @Column(name = "charge_type", nullable = false)
    private String chargeType;

    @Column(name = "affiliation_enabled", nullable = false)
    private boolean affiliationEnabled;

    @Column(nullable = false)
    private String status;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ProductEntity() {
        // Required by JPA.
    }

    private ProductEntity(Product product) {
        this.id = product.id();
        this.sellerId = product.sellerId();
        apply(product);
        this.createdAt = product.createdAt();
    }

    static ProductEntity from(Product product) {
        return new ProductEntity(product);
    }

    Product toDomain() {
        return new Product(id, sellerId, name, description, Segment.valueOf(segment),
                ChargeType.valueOf(chargeType), affiliationEnabled, ProductStatus.valueOf(status),
                archivedAt, createdAt);
    }

    void apply(Product product) {
        this.name = product.name();
        this.description = product.description();
        this.segment = product.segment().name();
        this.chargeType = product.chargeType().name();
        this.affiliationEnabled = product.affiliationEnabled();
        this.status = product.status().name();
        this.archivedAt = product.archivedAt();
    }
}
