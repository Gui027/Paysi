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
    boolean insertOrder(UUID id, UUID offerId, UUID buyerId, UUID affiliationId, String buyerSnapshotJson,
                         long amountCents, String method, String idempotencyKey, String requestHash, Instant now);

    void insertSubscription(Subscription subscription);

    void insertCharge(UUID id, UUID orderId, UUID subscriptionId, int cycleNumber, long amountCents,
                       String plan, int platformFeeBps, long platformFeeFixedCents, long platformFeeCents,
                       long affiliateFeeCents, long sellerAmountCents, String status, Instant now);

    void saveChargeResult(UUID chargeId, String status, String providerChargeId, long providerFeeCents,
                           Instant paidAt, Instant confirmedAt, Instant nextRetryAt);

    void markOrderStatus(UUID orderId, String status, Instant confirmedAt);

    void updateSubscriptionStatus(UUID subscriptionId, String status);

    void updateSubscriptionCycle(UUID subscriptionId, String status, Instant nextChargeAt);

    Optional<Subscription> findOwned(UUID sellerId, UUID subscriptionId);

    List<Subscription> listForSeller(UUID sellerId, Instant cursorCreatedAt, UUID cursorId, int limit);

    List<SubscriptionCharge> listCharges(UUID subscriptionId);

    boolean requestCancelAtPeriodEnd(UUID sellerId, UUID subscriptionId, Instant now);

    /** Assinaturas TRIAL cujo teste acabou, ou ACTIVE/PAST_DUE cujo ciclo vigente venceu (sem cancelamento pendente). */
    Optional<DueCycle> claimDueCycle(Instant now);

    /** Assinaturas com cancelamento pedido cujo ciclo vigente já terminou: viram CANCELED sem gerar nova cobrança. */
    Optional<UUID> claimDueCancellation(Instant now);

    void applyCancellation(UUID subscriptionId);

    /** Cobranças de ciclo (subscription_id preenchido) com status FAILED e retentativa vencida. */
    Optional<DueRetry> claimDueRetry(Instant now);

    void scheduleRetry(UUID chargeId, int attemptCount, Instant nextRetryAt);

    void exhaustRetry(UUID chargeId, UUID subscriptionId);

    record OrderReplay(UUID orderId, UUID subscriptionId, String requestHash) {
    }

    record DueCycle(UUID subscriptionId, UUID orderId, UUID offerId, UUID sellerId, long priceCents,
                     String cycle, String orderMethod, int boletoDueDays, int guaranteeDays, String buyerName,
                     String buyerEmail, String personType, String taxId, String providerToken, int nextCycleNumber,
                     boolean fromTrial, UUID affiliateId, int commissionBps, boolean affiliateAllCycles) {
    }

    record DueRetry(UUID chargeId, UUID subscriptionId, UUID orderId, UUID offerId, UUID sellerId,
                     long amountCents, int cycleNumber, int attemptCount, String cycle, int guaranteeDays,
                     String buyerName, String buyerEmail, String personType, String taxId, String providerToken,
                     UUID affiliateId, int commissionBps, boolean affiliateAllCycles) {
    }
}
