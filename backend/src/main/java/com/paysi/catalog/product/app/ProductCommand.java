package com.paysi.catalog.product.app;

import com.paysi.catalog.product.domain.ChargeType;
import com.paysi.catalog.product.domain.Segment;

public record ProductCommand(
        String name,
        String description,
        Segment segment,
        ChargeType chargeType,
        boolean affiliationEnabled
) {
}
