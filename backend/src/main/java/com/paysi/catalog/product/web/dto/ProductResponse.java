package com.paysi.catalog.product.web.dto;

import com.paysi.catalog.product.domain.ChargeType;
import com.paysi.catalog.product.domain.Product;
import com.paysi.catalog.product.domain.ProductStatus;
import com.paysi.catalog.product.domain.Segment;

import java.time.Instant;
import java.util.UUID;

public record ProductResponse(
        UUID id,
        String name,
        String description,
        Segment segment,
        ChargeType chargeType,
        boolean affiliationEnabled,
        ProductStatus status,
        Instant createdAt
) {
    public static ProductResponse from(Product product) {
        return new ProductResponse(product.id(), product.name(), product.description(), product.segment(),
                product.chargeType(), product.affiliationEnabled(), product.status(), product.createdAt());
    }
}
