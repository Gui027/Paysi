package com.paysi.subscription.adapter;

import com.paysi.subscription.domain.Subscription;
import com.paysi.subscription.domain.SubscriptionCharge;
import com.paysi.subscription.domain.SubscriptionStatus;
import com.paysi.subscription.port.SubscriptionRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class JdbcSubscriptionRepository implements SubscriptionRepository {
    private final JdbcTemplate jdbc;

    JdbcSubscriptionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<UUID> sellerIdForOffer(UUID offerId) {
        return jdbc.query("""
                SELECT p.seller_id FROM offers o JOIN products p ON p.id = o.product_id WHERE o.id = ?
                """, (rs, row) -> rs.getObject("seller_id", UUID.class), offerId).stream().findFirst();
    }

    @Override
    public Optional<UUID> findBuyer(String taxId, String email) {
        return jdbc.query("""
                SELECT id FROM buyers WHERE tax_id = ? AND email = ? AND anonymized_at IS NULL
                """, (rs, row) -> rs.getObject("id", UUID.class), taxId, email).stream().findFirst();
    }

    @Override
    public UUID insertBuyer(UUID id, String name, String email, String personType, String taxId,
                             String legalName, String municipalReg, String addressJson, Instant now) {
        jdbc.update("""
                INSERT INTO buyers (id, email, tax_id, person_type, name, legal_name, municipal_reg, address, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, cast(? as jsonb), ?)
                """, id, email, taxId, personType, name, legalName, municipalReg, addressJson, Timestamp.from(now));
        return id;
    }

    @Override
    public Optional<OrderReplay> findOrderByIdempotency(UUID offerId, String idempotencyKey) {
        return jdbc.query("""
                SELECT o.id AS order_id, o.request_hash, s.id AS subscription_id
                  FROM orders o LEFT JOIN subscriptions s ON s.order_id = o.id
                 WHERE o.offer_id = ? AND o.idempotency_key = ?
                """, (rs, row) -> new OrderReplay(rs.getObject("order_id", UUID.class),
                        rs.getObject("subscription_id", UUID.class), rs.getString("request_hash")),
                offerId, idempotencyKey).stream().findFirst();
    }

    @Override
    public boolean insertOrder(UUID id, UUID offerId, UUID buyerId, UUID affiliationId, String buyerSnapshotJson,
                                long amountCents, String method, String idempotencyKey, String requestHash,
                                Instant now) {
        try {
            jdbc.update("""
                    INSERT INTO orders
                      (id, offer_id, buyer_id, affiliation_id, buyer_snapshot, gross_cents, discount_cents,
                       paid_cents, method, installments, status, idempotency_key, request_hash, created_at)
                    VALUES (?, ?, ?, ?, cast(? as jsonb), ?, 0, ?, ?, 1, 'PENDING', ?, ?, ?)
                    """, id, offerId, buyerId, affiliationId, buyerSnapshotJson, amountCents, amountCents, method,
                    idempotencyKey, requestHash, Timestamp.from(now));
            return true;
        } catch (DataIntegrityViolationException collision) {
            return false;
        }
    }

    @Override
    public void insertSubscription(Subscription subscription) {
        jdbc.update("""
                INSERT INTO subscriptions
                  (id, order_id, offer_id, status, cycle_number, trial_ends_at, next_charge_at,
                   provider_token, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, subscription.id(), subscription.orderId(), subscription.offerId(),
                subscription.status().name(), subscription.cycleNumber(), timestamp(subscription.trialEndsAt()),
                timestamp(subscription.nextChargeAt()), subscription.providerToken(),
                Timestamp.from(subscription.createdAt()));
    }

    @Override
    public void insertCharge(UUID id, UUID orderId, UUID subscriptionId, int cycleNumber, long amountCents,
                              String plan, int platformFeeBps, long platformFeeFixedCents, long platformFeeCents,
                              long affiliateFeeCents, long sellerAmountCents, String status, Instant now) {
        try {
            jdbc.update("""
                    INSERT INTO charges
                      (id, order_id, subscription_id, cycle_number, amount_cents, plan, platform_fee_bps,
                       platform_fee_fixed_cents, platform_fee_cents, affiliate_fee_cents, seller_amount_cents,
                       status, attempt_count, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, ?)
                    """, id, orderId, subscriptionId, cycleNumber, amountCents, plan, platformFeeBps,
                    platformFeeFixedCents, platformFeeCents, affiliateFeeCents, sellerAmountCents, status,
                    Timestamp.from(now));
        } catch (DataIntegrityViolationException collision) {
            throw new IllegalStateException("Cobrança já registrada para este ciclo", collision);
        }
    }

    @Override
    public void saveChargeResult(UUID chargeId, String status, String providerChargeId, long providerFeeCents,
                                  Instant paidAt, Instant confirmedAt, Instant nextRetryAt) {
        jdbc.update("""
                UPDATE charges
                   SET status = ?, provider_charge_id = ?, provider_fee_cents = ?, paid_at = ?, confirmed_at = ?,
                       next_retry_at = ?
                 WHERE id = ?
                """, status, providerChargeId, providerFeeCents, timestamp(paidAt), timestamp(confirmedAt),
                timestamp(nextRetryAt), chargeId);
    }

    @Override
    public void markOrderStatus(UUID orderId, String status, Instant confirmedAt) {
        jdbc.update("UPDATE orders SET status = ?, confirmed_at = ? WHERE id = ?",
                status, timestamp(confirmedAt), orderId);
    }

    @Override
    public void updateSubscriptionStatus(UUID subscriptionId, String status) {
        jdbc.update("UPDATE subscriptions SET status = ? WHERE id = ?", status, subscriptionId);
    }

    @Override
    public void updateSubscriptionCycle(UUID subscriptionId, String status, Instant nextChargeAt) {
        jdbc.update("UPDATE subscriptions SET status = ?, next_charge_at = ? WHERE id = ?",
                status, timestamp(nextChargeAt), subscriptionId);
    }

    @Override
    public Optional<Subscription> findOwned(UUID sellerId, UUID subscriptionId) {
        return jdbc.query(SELECT + " AND s.id = ?", (rs, row) -> map(rs), sellerId, subscriptionId)
                .stream().findFirst();
    }

    @Override
    public List<Subscription> listForSeller(UUID sellerId, Instant cursorCreatedAt, UUID cursorId, int limit) {
        if (cursorCreatedAt == null) {
            return jdbc.query(SELECT + " ORDER BY s.created_at DESC, s.id DESC LIMIT ?",
                    (rs, row) -> map(rs), sellerId, limit);
        }
        return jdbc.query(SELECT + """
                 AND (s.created_at, s.id) < (?, ?)
                 ORDER BY s.created_at DESC, s.id DESC LIMIT ?
                """, (rs, row) -> map(rs), sellerId, Timestamp.from(cursorCreatedAt), cursorId, limit);
    }

    @Override
    public List<SubscriptionCharge> listCharges(UUID subscriptionId) {
        return jdbc.query("""
                SELECT id, cycle_number, amount_cents, status, attempt_count, next_retry_at, paid_at, created_at
                  FROM charges WHERE subscription_id = ? ORDER BY cycle_number DESC
                """, (rs, row) -> new SubscriptionCharge(rs.getObject("id", UUID.class),
                        rs.getInt("cycle_number"), rs.getLong("amount_cents"), rs.getString("status"),
                        rs.getInt("attempt_count"), instant(rs, "next_retry_at"), instant(rs, "paid_at"),
                        instant(rs, "created_at")), subscriptionId);
    }

    @Override
    public boolean requestCancelAtPeriodEnd(UUID sellerId, UUID subscriptionId, Instant now) {
        return jdbc.update("""
                UPDATE subscriptions s SET canceled_at = ?
                  FROM orders o, offers of, products p
                 WHERE s.id = ? AND s.order_id = o.id AND o.offer_id = of.id AND of.product_id = p.id
                   AND p.seller_id = ? AND s.status <> 'CANCELED' AND s.canceled_at IS NULL
                """, Timestamp.from(now), subscriptionId, sellerId) == 1;
    }

    @Override
    public Optional<DueCycle> claimDueCycle(Instant now) {
        return jdbc.query("""
                SELECT s.id AS subscription_id, s.order_id, of.id AS offer_id, p.seller_id,
                       of.amount_cents, of.cycle, o.method AS order_method, of.boleto_due_days, of.guarantee_days,
                       o.buyer_snapshot ->> 'name' AS buyer_name, o.buyer_snapshot ->> 'email' AS buyer_email,
                       o.buyer_snapshot ->> 'personType' AS person_type, o.buyer_snapshot ->> 'taxId' AS tax_id,
                       s.provider_token, s.cycle_number, s.status,
                       a.affiliate_id, a.commission_bps, a.recurring
                  FROM subscriptions s
                  JOIN orders o ON o.id = s.order_id
                  JOIN offers of ON of.id = o.offer_id
                  JOIN products p ON p.id = of.product_id
                  LEFT JOIN affiliations a ON a.id = o.affiliation_id AND a.status = 'APPROVED'
                 WHERE s.canceled_at IS NULL AND s.status IN ('TRIAL', 'ACTIVE', 'PAST_DUE')
                   AND s.next_charge_at IS NOT NULL AND s.next_charge_at <= ?
                 ORDER BY s.next_charge_at
                 LIMIT 1 FOR UPDATE OF s SKIP LOCKED
                """, (rs, row) -> {
                    boolean fromTrial = "TRIAL".equals(rs.getString("status"));
                    int nextCycleNumber = fromTrial ? 1 : rs.getInt("cycle_number") + 1;
                    UUID affiliateId = rs.getObject("affiliate_id", UUID.class);
                    boolean allCycles = rs.getBoolean("recurring");
                    boolean commissionApplies = affiliateId != null && (nextCycleNumber == 1 || allCycles);
                    return new DueCycle(rs.getObject("subscription_id", UUID.class),
                            rs.getObject("order_id", UUID.class), rs.getObject("offer_id", UUID.class),
                            rs.getObject("seller_id", UUID.class), rs.getLong("amount_cents"),
                            rs.getString("cycle"), rs.getString("order_method"), rs.getInt("boleto_due_days"),
                            rs.getInt("guarantee_days"), rs.getString("buyer_name"), rs.getString("buyer_email"),
                            rs.getString("person_type"), rs.getString("tax_id"), rs.getString("provider_token"),
                            nextCycleNumber, fromTrial, commissionApplies ? affiliateId : null,
                            commissionApplies ? rs.getInt("commission_bps") : 0, allCycles);
                }, Timestamp.from(now)).stream().findFirst();
    }

    @Override
    public Optional<UUID> claimDueCancellation(Instant now) {
        return jdbc.query("""
                SELECT id FROM subscriptions
                 WHERE canceled_at IS NOT NULL AND status <> 'CANCELED'
                   AND next_charge_at IS NOT NULL AND next_charge_at <= ?
                 ORDER BY next_charge_at
                 LIMIT 1 FOR UPDATE SKIP LOCKED
                """, (rs, row) -> rs.getObject("id", UUID.class), Timestamp.from(now)).stream().findFirst();
    }

    @Override
    public void applyCancellation(UUID subscriptionId) {
        jdbc.update("UPDATE subscriptions SET status = 'CANCELED' WHERE id = ?", subscriptionId);
    }

    @Override
    public Optional<DueRetry> claimDueRetry(Instant now) {
        return jdbc.query("""
                SELECT c.id AS charge_id, c.subscription_id, c.order_id, o.offer_id, p.seller_id,
                       c.amount_cents, c.cycle_number, c.attempt_count, of.cycle, of.guarantee_days,
                       o.buyer_snapshot ->> 'name' AS buyer_name, o.buyer_snapshot ->> 'email' AS buyer_email,
                       o.buyer_snapshot ->> 'personType' AS person_type, o.buyer_snapshot ->> 'taxId' AS tax_id,
                       s.provider_token, a.affiliate_id, a.commission_bps, a.recurring
                  FROM charges c
                  JOIN subscriptions s ON s.id = c.subscription_id
                  JOIN orders o ON o.id = c.order_id
                  JOIN offers of ON of.id = o.offer_id
                  JOIN products p ON p.id = of.product_id
                  LEFT JOIN affiliations a ON a.id = o.affiliation_id AND a.status = 'APPROVED'
                 WHERE c.status = 'FAILED' AND c.subscription_id IS NOT NULL
                   AND c.next_retry_at IS NOT NULL AND c.next_retry_at <= ?
                 ORDER BY c.next_retry_at
                 LIMIT 1 FOR UPDATE OF c SKIP LOCKED
                """, (rs, row) -> {
                    UUID affiliateId = rs.getObject("affiliate_id", UUID.class);
                    boolean allCycles = rs.getBoolean("recurring");
                    int cycleNumber = rs.getInt("cycle_number");
                    boolean commissionApplies = affiliateId != null && (cycleNumber == 1 || allCycles);
                    return new DueRetry(rs.getObject("charge_id", UUID.class),
                            rs.getObject("subscription_id", UUID.class), rs.getObject("order_id", UUID.class),
                            rs.getObject("offer_id", UUID.class), rs.getObject("seller_id", UUID.class),
                            rs.getLong("amount_cents"), cycleNumber, rs.getInt("attempt_count"),
                            rs.getString("cycle"), rs.getInt("guarantee_days"), rs.getString("buyer_name"),
                            rs.getString("buyer_email"), rs.getString("person_type"), rs.getString("tax_id"),
                            rs.getString("provider_token"), commissionApplies ? affiliateId : null,
                            commissionApplies ? rs.getInt("commission_bps") : 0, allCycles);
                }, Timestamp.from(now)).stream().findFirst();
    }

    @Override
    public void scheduleRetry(UUID chargeId, int attemptCount, Instant nextRetryAt) {
        jdbc.update("UPDATE charges SET attempt_count = ?, next_retry_at = ? WHERE id = ?",
                attemptCount, timestamp(nextRetryAt), chargeId);
    }

    @Override
    public void exhaustRetry(UUID chargeId, UUID subscriptionId) {
        jdbc.update("UPDATE charges SET next_retry_at = NULL WHERE id = ?", chargeId);
        jdbc.update("UPDATE subscriptions SET status = 'CANCELED' WHERE id = ?", subscriptionId);
    }

    private static final String SELECT = """
            SELECT s.id, s.order_id, s.offer_id, s.status, s.cycle_number, s.trial_ends_at,
                   s.next_charge_at, s.canceled_at, s.provider_token, s.created_at
              FROM subscriptions s
              JOIN orders o ON o.id = s.order_id
              JOIN offers of ON of.id = o.offer_id
              JOIN products p ON p.id = of.product_id
             WHERE p.seller_id = ?
            """;

    private Subscription map(ResultSet rs) throws SQLException {
        return new Subscription(rs.getObject("id", UUID.class), rs.getObject("order_id", UUID.class),
                rs.getObject("offer_id", UUID.class), SubscriptionStatus.valueOf(rs.getString("status")),
                rs.getInt("cycle_number"), instant(rs, "trial_ends_at"), instant(rs, "next_charge_at"),
                instant(rs, "canceled_at"), rs.getString("provider_token"), instant(rs, "created_at"));
    }

    private static Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        var timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }
}
