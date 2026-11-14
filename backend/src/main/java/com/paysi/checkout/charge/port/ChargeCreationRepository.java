package com.paysi.checkout.charge.port;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface ChargeCreationRepository {

    Optional<OrderContext> findOrderContext(UUID orderId);

    /** Uma cobrança por pedido nesta versão (BE-07.1): reenviar o mesmo pedido é idempotente. */
    Optional<UUID> findChargeForOrder(UUID orderId);

    Optional<UUID> findSubscriptionForOrder(UUID orderId);

    UUID insertOrReadSubscription(UUID id, UUID orderId, UUID offerId, String providerToken, Instant now);

    void insertCharge(UUID id, UUID orderId, UUID subscriptionId, long amountCents, String plan, int platformFeeBps,
                       long platformFeeFixedCents, long platformFeeCents, long affiliateFeeCents,
                       long sellerAmountCents, String status, Instant now);

    Optional<RecurringCharge> findRecurringCharge(UUID chargeId);

    void updateSubscriptionPayment(UUID subscriptionId, String status, Instant nextChargeAt,
                                   UUID chargeId, Instant nextRetryAt);

    /** Leitura sem lock, para o front consultar o status enquanto aguarda Pix/boleto confirmar. */
    Optional<ChargeView> findChargeView(UUID chargeId);

    /** Leitura da cobrança restrita ao vendedor dono do produto. */
    default Optional<ChargeView> findChargeView(UUID sellerId, UUID chargeId) {
        return Optional.empty();
    }

    record OrderContext(UUID offerId, UUID sellerId, UUID affiliateId, int commissionBps,
                         String chargeType, String cycle, long paidCents, String method,
                         int installments, String buyerName, String buyerEmail, String personType,
                         String taxId, int boletoDueDays, int guaranteeDays) {
        public boolean recurring() {
            return "SUBSCRIPTION".equals(chargeType);
        }
    }

    record RecurringCharge(UUID subscriptionId, String cycle) { }

    record ChargeView(UUID chargeId, String method, String status, String boletoBarcode, String boletoUrl,
                       String pixQrCode, Instant expiresAt) {
    }
}
