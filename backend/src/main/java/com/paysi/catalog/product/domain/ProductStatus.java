package com.paysi.catalog.product.domain;

/** Statuses already accepted by V003. BE-03.1 only creates DRAFT products. */
public enum ProductStatus {
    DRAFT,
    ACTIVE,
    PAUSED,
    SUSPENDED
}
