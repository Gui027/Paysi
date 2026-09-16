package com.paysi.checkout.order.adapter;

import com.paysi.catalog.offer.domain.OfferPaymentMethod;
import com.paysi.checkout.order.domain.Order;
import com.paysi.checkout.order.domain.OrderStatus;
import com.paysi.checkout.order.port.OrderRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;

@Repository
class JdbcOrderRepository implements OrderRepository {
    private final JdbcTemplate jdbc;

    JdbcOrderRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean insertIfAbsent(Order order) {
        // uq_orders_idem (offer_id, idempotency_key) é a autoridade da idempotência.
        // Se outra requisição idêntica estiver em voo, o Postgres bloqueia aqui até
        // ela decidir, e devolvemos zero linhas — nunca um segundo pedido.
        return jdbc.update("""
                INSERT INTO orders
                  (id, offer_id, buyer_id, affiliation_id, buyer_snapshot, gross_cents,
                   discount_cents, coupon_id, paid_cents, method, installments, status,
                   idempotency_key, request_hash, created_at)
                VALUES (?, ?, ?, ?, cast(? as jsonb), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (offer_id, idempotency_key) DO NOTHING
                """, order.id(), order.offerId(), order.buyerId(), order.affiliationId(),
                order.buyerSnapshot(), order.grossCents(), order.discountCents(),
                order.couponId(), order.paidCents(), order.method().name(), order.installments(),
                order.status().name(), order.idempotencyKey(), order.requestHash(),
                Timestamp.from(order.createdAt())) == 1;
    }

    @Override
    public Optional<Order> findByIdempotencyKey(UUID offerId, String idempotencyKey) {
        return jdbc.query("SELECT * FROM orders WHERE offer_id = ? AND idempotency_key = ?",
                (rs, row) -> map(rs), offerId, idempotencyKey).stream().findFirst();
    }

    private static Order map(ResultSet rs) throws SQLException {
        return new Order(rs.getObject("id", UUID.class), rs.getObject("offer_id", UUID.class),
                rs.getObject("buyer_id", UUID.class), rs.getObject("affiliation_id", UUID.class),
                rs.getString("buyer_snapshot"), rs.getLong("gross_cents"),
                rs.getLong("discount_cents"), rs.getObject("coupon_id", UUID.class),
                rs.getLong("paid_cents"), OfferPaymentMethod.valueOf(rs.getString("method")),
                rs.getInt("installments"), OrderStatus.valueOf(rs.getString("status")),
                rs.getString("idempotency_key"), rs.getString("request_hash"),
                rs.getTimestamp("created_at").toInstant());
    }
}
