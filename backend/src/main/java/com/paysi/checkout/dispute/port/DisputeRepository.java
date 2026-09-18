package com.paysi.checkout.dispute.port;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DisputeRepository {

    /** Trava a cobrança para contestação, já escopada ao vendedor dono (autorização entre contas). */
    Optional<ChargeDisputeContext> lockChargeForDispute(UUID sellerId, UUID chargeId);

    Optional<StoredDispute> findByProviderDisputeId(String providerDisputeId);

    /** Trava a disputa para resolução (WON/LOST), já escopada ao vendedor dono da cobrança contestada. */
    Optional<StoredDispute> lockDisputeForResolution(UUID sellerId, UUID disputeId);

    /** Leitura sem bloqueio, escopada ao vendedor dono — para montar o pacote de defesa. */
    Optional<StoredDispute> findDisputeForSeller(UUID sellerId, UUID disputeId);

    /** false = corrida perdida (outra requisição com o mesmo provider_dispute_id já inseriu). */
    boolean insertDispute(UUID id, UUID chargeId, long amountCents, long acquirerFeeCents, String reason,
                           String status, Instant deadlineAt, String providerDisputeId, Instant createdAt);

    void updateDisputeStatus(UUID disputeId, String status);

    void applyChargeDisputeStatus(UUID chargeId, String status);

    /** Alocação por bucket do débito original do vendedor na abertura da disputa (para reversão exata em WON). */
    List<BucketAmount> sellerOpeningAllocation(UUID disputeId);

    /** Alocação por bucket do débito original do afiliado na abertura da disputa (para reversão exata em WON). */
    List<BucketAmount> affiliateOpeningAllocation(UUID disputeId);

    Optional<SaleEvidenceSnapshot> findEvidence(UUID chargeId);

    void recordEmailDelivered(UUID chargeId, Instant deliveredAt);

    void recordEmailOpened(UUID chargeId, Instant openedAt);

    void appendAccessLog(UUID chargeId, String entryJson);

    record ChargeDisputeContext(UUID sellerId, UUID affiliateId, long paidCents, long refundedCents, String status,
                                 long sellerAmountCents, long affiliateFeeCents, long platformFeeCents,
                                 Long providerFeeCents) {
    }

    record StoredDispute(UUID id, UUID chargeId, UUID sellerId, UUID affiliateId, long amountCents,
                          long acquirerFeeCents, String reason, String status, Instant deadlineAt,
                          String providerDisputeId, Instant createdAt) {
    }

    record BucketAmount(String bucket, long amountCents) {
    }

    record SaleEvidenceSnapshot(UUID chargeId, String ip, String userAgent, String deviceKey, String termsHash,
                                 Instant termsAcceptedAt, String threeDsResult, Instant emailDeliveredAt,
                                 Instant emailOpenedAt, String accessLogJson) {
    }
}
