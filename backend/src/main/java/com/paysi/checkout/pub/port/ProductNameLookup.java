package com.paysi.checkout.pub.port;

import java.util.Optional;
import java.util.UUID;

public interface ProductNameLookup {
    Optional<String> findActiveProductName(UUID productId);
}
