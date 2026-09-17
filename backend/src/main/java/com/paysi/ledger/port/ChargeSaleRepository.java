package com.paysi.ledger.port;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface ChargeSaleRepository {
    Optional<ChargeSale> findChargeSale(UUID chargeId);

    record ChargeSale(UUID sellerId, long sellerAmountCents, long platformFeeCents, UUID affiliateId,
                       long affiliateFeeCents, int guaranteeDays) {
    }
}
