package com.paysi.payment.card.port;

import com.paysi.payment.card.domain.SaleEvidenceCommand;
import com.paysi.payment.provider.*;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface CardPaymentRepository {
    Optional<CardChargeContext> lockCharge(UUID chargeId);
    void saveResult(UUID chargeId, ProviderPaymentResult result, Instant pixAlternativeExpiresAt, Instant now);
    void saveEvidence(UUID chargeId, SaleEvidenceCommand evidence, ProviderThreeDs threeDs);

    record CardChargeContext(UUID chargeId, UUID orderId, long amountCents, int installments,
                             ProviderBuyer buyer, ProviderSplit split, String providerChargeId,
                             ProviderChargeStatus providerStatus, String threeDsStatus,
                             String challengeUrl, String eci, Instant pixAlternativeExpiresAt) {}
}
