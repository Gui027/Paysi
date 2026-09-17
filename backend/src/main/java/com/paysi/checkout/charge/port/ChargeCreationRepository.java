package com.paysi.checkout.charge.port;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface ChargeCreationRepository {

    Optional<OrderContext> findOrderContext(UUID orderId);

    /** Uma cobrança por pedido nesta versão (BE-07.1): reenviar o mesmo pedido é idempotente. */
    Optional<UUID> findChargeForOrder(UUID orderId);

    void insertCharge(UUID id, UUID orderId, long amountCents, String plan, int platformFeeBps,
                       long platformFeeFixedCents, long platformFeeCents, long affiliateFeeCents,
                       long sellerAmountCents, String status, Instant now);

    /** Leitura sem lock, para o front consultar o status enquanto aguarda Pix/boleto confirmar. */
    Optional<ChargeView> findChargeView(UUID chargeId);

    record OrderContext(UUID sellerId, UUID affiliateId, int commissionBps, long paidCents, String method,
                         int installments, String buyerName, String buyerEmail, String personType,
                         String taxId, int boletoDueDays, int guaranteeDays) {
    }

    record ChargeView(UUID chargeId, String method, String status, String boletoBarcode, String boletoUrl,
                       String pixQrCode, Instant expiresAt) {
    }
}
