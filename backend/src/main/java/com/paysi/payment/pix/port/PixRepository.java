package com.paysi.payment.pix.port;

import com.paysi.payment.provider.*;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface PixRepository {
    Optional<PixChargeContext> lockCharge(UUID chargeId);

    void saveIssued(UUID chargeId, ProviderPaymentResult result);

    record PixChargeContext(UUID chargeId, UUID orderId, long amountCents, ProviderBuyer buyer,
                            ProviderSplit split, String providerChargeId, ProviderChargeStatus providerStatus,
                            String qrCode, Instant expiresAt) {
    }
}
