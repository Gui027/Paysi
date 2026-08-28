package com.paysi.payment.boleto.port;

import com.paysi.payment.provider.*;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface BoletoRepository {
    Optional<BoletoChargeContext> lockCharge(UUID chargeId);
    void saveIssued(UUID chargeId, ProviderPaymentResult result);
    int expireDue(Instant now);

    record BoletoChargeContext(UUID chargeId, UUID orderId, long amountCents,
                               ProviderBuyer buyer, ProviderSplit split, String providerChargeId,
                               ProviderChargeStatus providerStatus, String barcode, String pdfUrl,
                               Instant dueAt) {}
}
