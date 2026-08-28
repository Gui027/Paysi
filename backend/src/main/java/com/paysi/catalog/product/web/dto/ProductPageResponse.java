package com.paysi.catalog.product.web.dto;

import com.paysi.catalog.product.app.ProductPage;

import java.util.List;

public record ProductPageResponse(List<ProductResponse> items, String nextCursor) {
    public static ProductPageResponse from(ProductPage page) {
        return new ProductPageResponse(page.items().stream().map(ProductResponse::from).toList(),
                page.nextCursor());
    }
}
