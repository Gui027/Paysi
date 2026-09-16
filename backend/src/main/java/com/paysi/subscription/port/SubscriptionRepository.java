package com.paysi.subscription.port;

import com.paysi.subscription.domain.Subscription;
import com.paysi.subscription.domain.SubscriptionCharge;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubscriptionRepository {

    Optional<UUID> sellerIdForOffer(UUID offerId);

    Optional<UUID> findBuyer(String taxId, String email);

    UUID insertBuyer(UUID id, String name, String email, String personType, String taxId,
                      String legalName, String municipalReg, String addressJson, Instant now);

    /** Índice único (offer_id, idempotency_key): devolve o pedido existente para decidir replay x 409. */
    Optional<OrderReplay> findOrderByIdempotency(UUID offerId, String idempotencyKey);

    /** {@code true} se inseriu; {@code false} se colidiu com uma corrida concorrente na mesma chave. */
    boolean insertOrder(UUID id, UUID offerId, UUID buyerId, String buyerSnapshotJson,
                         long amountCents, String idempotencyKey, String requestHash, Instant now);

    void insertSubscription(Subscription subscription);

    void insertCharge(UUID id, UUID orderId, UUID subscriptionId, int cycleNumber, long amountCents,
                       String plan, int platformFeeBps, long platformFeeFixedCents, long platformFeeCents,
                       long affiliateFeeCents, long sellerAmountCents, String status, Instant now);

    void saveChargeResult(UUID chargeId, String status, String providerChargeId, long providerFeeCents,
                           Instant paidAt, Instant confirmedAt);

    void markOrderStatus(UUID orderId, String status, Instant confirmedAt);

    void updateSubscriptionStatus(UUID subscriptionId, String status);

    void updateSubscriptionCycle(UUID subscriptionId, String status, Instant nextChargeAt);

    Optional<Subscription> findOwned(UUID sellerId, UUID subscriptionId);

    List<Subscription> listForSeller(UUID sellerId, Instant cursorCreatedAt, UUID cursorId, int limit);

    List<SubscriptionCharge> listCharges(UUID subscriptionId);

    boolean requestCancelAtPeriodEnd(UUID sellerId, UUID subscriptionId, Instant now);

    record OrderReplay(UUID orderId, UUID subscriptionId, String requestHash) {
    }
}
