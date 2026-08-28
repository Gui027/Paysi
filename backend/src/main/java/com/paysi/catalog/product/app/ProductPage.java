package com.paysi.catalog.product.app;

import com.paysi.catalog.product.domain.Product;

import java.util.List;

public record ProductPage(List<Product> items, String nextCursor) {
}
