package com.paysi.catalog.offer.adapter;

import com.paysi.catalog.offer.domain.BillingCycle;
import com.paysi.catalog.offer.domain.Offer;
import com.paysi.catalog.offer.domain.OfferPaymentMethod;
import com.paysi.catalog.offer.domain.OfferPayoutDelay;
import com.paysi.catalog.offer.domain.OfferStatus;
import com.paysi.catalog.offer.port.OfferRepository;
import com.paysi.catalog.product.domain.ChargeType;
import com.paysi.catalog.product.domain.Segment;
import com.paysi.core.error.ConflictException;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
class JdbcOfferRepository implements OfferRepository {
    private static final String SELECT = """
            SELECT o.*
              FROM offers o
              JOIN products p ON p.id = o.product_id
             WHERE p.seller_id = ?
               AND p.archived_at IS NULL
               AND o.archived_at IS NULL
            """;

    private final JdbcTemplate jdbc;

    JdbcOfferRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(Offer offer) {
        jdbc.update("""
                INSERT INTO offers
                  (id, product_id, charge_type, segment, slug, amount_cents, cycle,
                   trial_days, trial_requires_card, guarantee_days, max_installments,
                   boleto_due_days, boleto_cycle_lead_days, payout_delay, status,
                   created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, offer.id(), offer.productId(), offer.chargeType().name(), offer.segment().name(),
                offer.slug(), offer.priceCents(), name(offer.cycle()), offer.trialDays(),
                offer.trialRequiresCard(), offer.guaranteeDays(), offer.maxInstallments(),
                offer.boletoDueDays(), offer.boletoAdvanceDays(), offer.payoutDelay().name(),
                offer.status().name(), offer.createdAt(), offer.updatedAt());
        replaceMethods(offer);
    }

    @Override
    public List<Offer> listActiveOwned(UUID sellerId, UUID productId) {
        return jdbc.query(SELECT + " AND o.product_id = ? ORDER BY o.created_at DESC, o.id DESC",
                (rs, row) -> map(rs), sellerId, productId);
    }

    @Override
    public Optional<Offer> findActiveOwned(UUID sellerId, UUID offerId) {
        return jdbc.query(SELECT + " AND o.id = ?", (rs, row) -> map(rs), sellerId, offerId)
                .stream().findFirst();
    }

    @Override
    public void update(Offer offer) {
        try {
            int changed = jdbc.update("""
                    UPDATE offers
                       SET amount_cents = ?, cycle = ?, trial_days = ?, trial_requires_card = ?,
                           guarantee_days = ?, max_installments = ?, boleto_due_days = ?,
                           boleto_cycle_lead_days = ?, payout_delay = ?, updated_at = ?
                     WHERE id = ? AND archived_at IS NULL
                    """, offer.priceCents(), name(offer.cycle()), offer.trialDays(),
                    offer.trialRequiresCard(), offer.guaranteeDays(), offer.maxInstallments(),
                    offer.boletoDueDays(), offer.boletoAdvanceDays(), offer.payoutDelay().name(),
                    offer.updatedAt(), offer.id());
            if (changed != 1) throw new IllegalStateException("Oferta desapareceu durante a atualização");
            replaceMethods(offer);
        } catch (DataAccessException error) {
            if (containsMessage(error, "cycle e guarantee_days imutaveis apos a primeira venda paga")) {
                throw new ConflictException("OFFER_SALE_TERMS_IMMUTABLE",
                        "Ciclo e garantia não podem mudar após a primeira venda paga", null);
            }
            throw error;
        }
    }

    @Override
    public boolean archive(UUID sellerId, UUID offerId, Instant archivedAt) {
        return jdbc.update("""
                UPDATE offers o
                   SET archived_at = ?, status = 'ARCHIVED', updated_at = ?
                  FROM products p
                 WHERE p.id = o.product_id
                   AND p.seller_id = ?
                   AND p.archived_at IS NULL
                   AND o.id = ?
                   AND o.archived_at IS NULL
                """, archivedAt, archivedAt, sellerId, offerId) == 1;
    }

    private Offer map(ResultSet rs) throws SQLException {
        UUID id = rs.getObject("id", UUID.class);
        return new Offer(id, rs.getObject("product_id", UUID.class),
                ChargeType.valueOf(rs.getString("charge_type")), Segment.valueOf(rs.getString("segment")),
                rs.getString("slug"), rs.getLong("amount_cents"), nullableCycle(rs.getString("cycle")),
                rs.getInt("trial_days"), rs.getBoolean("trial_requires_card"),
                rs.getInt("guarantee_days"), rs.getInt("max_installments"),
                rs.getInt("boleto_due_days"), rs.getInt("boleto_cycle_lead_days"), methods(id),
                OfferPayoutDelay.valueOf(rs.getString("payout_delay")),
                OfferStatus.valueOf(rs.getString("status")),
                instant(rs, "archived_at"), instant(rs, "created_at"), instant(rs, "updated_at"));
    }

    private Set<OfferPaymentMethod> methods(UUID offerId) {
        List<OfferPaymentMethod> values = jdbc.query("""
                SELECT method FROM offer_payment_methods WHERE offer_id = ? ORDER BY method
                """, (rs, row) -> OfferPaymentMethod.valueOf(rs.getString("method")), offerId);
        return values.isEmpty() ? Set.of() : EnumSet.copyOf(values);
    }

    private void replaceMethods(Offer offer) {
        jdbc.update("DELETE FROM offer_payment_methods WHERE offer_id = ?", offer.id());
        jdbc.batchUpdate("INSERT INTO offer_payment_methods (offer_id, method) VALUES (?, ?)",
                offer.paymentMethods(), offer.paymentMethods().size(),
                (statement, method) -> {
                    statement.setObject(1, offer.id());
                    statement.setString(2, method.name());
                });
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        var timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static String name(Enum<?> value) {
        return value == null ? null : value.name();
    }

    private static BillingCycle nullableCycle(String value) {
        return value == null ? null : BillingCycle.valueOf(value);
    }

    private static boolean containsMessage(Throwable error, String expected) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (String.valueOf(cause.getMessage()).contains(expected)) return true;
        }
        return false;
    }
}
