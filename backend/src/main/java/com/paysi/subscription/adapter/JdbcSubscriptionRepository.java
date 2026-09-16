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
    public boolean insertOrder(UUID id, UUID offerId, UUID buyerId, String buyerSnapshotJson,
                                long amountCents, String idempotencyKey, String requestHash, Instant now) {
        try {
            jdbc.update("""
                    INSERT INTO orders
                      (id, offer_id, buyer_id, buyer_snapshot, gross_cents, discount_cents, paid_cents,
                       method, installments, status, idempotency_key, request_hash, created_at)
                    VALUES (?, ?, ?, cast(? as jsonb), ?, 0, ?, 'CARD', 1, 'PENDING', ?, ?, ?)
                    """, id, offerId, buyerId, buyerSnapshotJson, amountCents, amountCents,
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
                                  Instant paidAt, Instant confirmedAt) {
        jdbc.update("""
                UPDATE charges
                   SET status = ?, provider_charge_id = ?, provider_fee_cents = ?, paid_at = ?, confirmed_at = ?
                 WHERE id = ?
                """, status, providerChargeId, providerFeeCents, timestamp(paidAt), timestamp(confirmedAt), chargeId);
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
