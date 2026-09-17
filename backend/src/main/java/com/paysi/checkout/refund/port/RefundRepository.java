package com.paysi.checkout.refund.port;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefundRepository {

    /** Trava a cobrança para reembolso, já escopada ao vendedor dono (autorização entre contas). */
    Optional<ChargeRefundContext> lockChargeForRefund(UUID sellerId, UUID chargeId);

    Optional<StoredRefund> findByIdempotencyKey(UUID chargeId, String idempotencyKey);

    /** false = corrida perdida (outra requisição com a mesma chave já inseriu). */
    boolean insertRefund(UUID id, UUID chargeId, long amountCents, long sellerCents, long affiliateCents,
                          long platformCents, long providerCents, String reason, String status,
                          String providerRefundId, String idempotencyKey, String requestedBy,
                          Instant createdAt, Instant settledAt);

    void applyChargeRefund(UUID chargeId, long newRefundedCents, String newStatus);

    record ChargeRefundContext(UUID sellerId, UUID affiliateId, String providerChargeId, long paidCents,
                                long refundedCents, String status, long sellerAmountCents, long affiliateFeeCents,
                                long platformFeeCents, Long providerFeeCents) {
    }

    record StoredRefund(UUID id, long amountCents, long sellerCents, long affiliateCents, long platformCents,
                         long providerCents, String status, long chargeRefundedCentsAfter, String chargeStatus) {
    }
}
