package com.paysi.affiliate.adapter;

import com.paysi.affiliate.app.AffiliateCursor;
import com.paysi.affiliate.app.MarketplaceItem;
import com.paysi.affiliate.domain.Affiliation;
import com.paysi.affiliate.domain.AffiliationEndReason;
import com.paysi.affiliate.domain.AffiliationRecurrence;
import com.paysi.affiliate.domain.AffiliationStatus;
import com.paysi.affiliate.port.AffiliateRepository;
import com.paysi.catalog.offer.domain.OfferPayoutDelay;
import com.paysi.catalog.product.domain.ChargeType;
import com.paysi.catalog.product.domain.Segment;
import com.paysi.core.error.ConflictException;
import org.springframework.dao.DuplicateKeyException;
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
class JdbcAffiliateRepository implements AffiliateRepository {
    private static final String MARKETPLACE_SELECT = """
            SELECT p.id AS product_id, p.seller_id, p.name AS product_name, p.description,
                   seller.full_name AS seller_name, p.segment, p.charge_type, p.created_at,
                   published.amount_cents, published.suggested_bps, published.guarantee_days,
                   published.payout_delay
              FROM products p
              JOIN accounts seller ON seller.id = p.seller_id
              JOIN LATERAL (
                    SELECT o.amount_cents, o.suggested_bps, o.guarantee_days, o.payout_delay
                      FROM offers o
                     WHERE o.product_id = p.id
                       AND o.status = 'PUBLISHED'
                       AND o.archived_at IS NULL
                     ORDER BY o.amount_cents, o.created_at, o.id
                     LIMIT 1
              ) published ON true
             WHERE p.affiliation_enabled = true
               AND p.status = 'ACTIVE'
               AND p.archived_at IS NULL
               AND seller.status = 'ACTIVE'
            """;

    private static final String AFFILIATION_SELECT = """
            SELECT af.*, p.seller_id, p.name AS product_name,
                   seller.full_name AS seller_name, affiliate.full_name AS affiliate_name
              FROM affiliations af
              JOIN products p ON p.id = af.product_id
              JOIN accounts seller ON seller.id = p.seller_id
              JOIN accounts affiliate ON affiliate.id = af.affiliate_id
             WHERE 1 = 1
            """;

    private final JdbcTemplate jdbc;

    JdbcAffiliateRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<MarketplaceItem> listMarketplace(AffiliateCursor cursor, int limit) {
        String page = cursor == null ? "" : " AND (p.created_at < ? OR (p.created_at = ? AND p.id < ?))";
        String sql = MARKETPLACE_SELECT + page + " ORDER BY p.created_at DESC, p.id DESC LIMIT ?";
        return cursor == null
                ? jdbc.query(sql, (rs, row) -> marketplace(rs), limit)
                : jdbc.query(sql, (rs, row) -> marketplace(rs), Timestamp.from(cursor.createdAt()),
                        Timestamp.from(cursor.createdAt()), cursor.id(), limit);
    }

    @Override
    public Optional<MarketplaceItem> findMarketplaceProduct(UUID productId) {
        return jdbc.query(MARKETPLACE_SELECT + " AND p.id = ?", (rs, row) -> marketplace(rs), productId)
                .stream().findFirst();
    }

    @Override
    public Affiliation request(UUID affiliationId, UUID productId, UUID affiliateId, Instant now) {
        try {
            jdbc.update("""
                    INSERT INTO affiliations
                        (id, product_id, affiliate_id, commission_bps, recurring, status, created_at)
                    VALUES (?, ?, ?, 0, false, 'REQUESTED', ?)
                    """, affiliationId, productId, affiliateId, Timestamp.from(now));
        } catch (DuplicateKeyException error) {
            throw new ConflictException("AFFILIATION_ALREADY_ACTIVE",
                    "Já existe uma solicitação ou afiliação ativa para este produto", "productId");
        }
        return find(affiliationId).orElseThrow();
    }

    @Override
    public Optional<Affiliation> find(UUID affiliationId) {
        return jdbc.query(AFFILIATION_SELECT + " AND af.id = ?", (rs, row) -> affiliation(rs), affiliationId)
                .stream().findFirst();
    }

    @Override
    public List<Affiliation> listForAffiliate(UUID affiliateId, AffiliateCursor cursor, int limit) {
        return list(" AND af.affiliate_id = ?", affiliateId, cursor, limit);
    }

    @Override
    public List<Affiliation> listForSeller(UUID sellerId, AffiliateCursor cursor, int limit) {
        return list(" AND p.seller_id = ?", sellerId, cursor, limit);
    }

    private List<Affiliation> list(String ownerClause, UUID ownerId, AffiliateCursor cursor, int limit) {
        String page = cursor == null ? "" : " AND (af.created_at < ? OR (af.created_at = ? AND af.id < ?))";
        String sql = AFFILIATION_SELECT + ownerClause + page + " ORDER BY af.created_at DESC, af.id DESC LIMIT ?";
        return cursor == null
                ? jdbc.query(sql, (rs, row) -> affiliation(rs), ownerId, limit)
                : jdbc.query(sql, (rs, row) -> affiliation(rs), ownerId, Timestamp.from(cursor.createdAt()),
                        Timestamp.from(cursor.createdAt()), cursor.id(), limit);
    }

    @Override
    public Optional<Affiliation> approve(UUID affiliationId, UUID sellerId, int commissionBps,
                                         AffiliationRecurrence recurrence, Instant now) {
        int changed = jdbc.update("""
                UPDATE affiliations af
                   SET status = 'APPROVED', commission_bps = ?, recurring = ?, approved_at = ?
                 WHERE af.id = ?
                   AND af.status = 'REQUESTED'
                   AND EXISTS (SELECT 1 FROM products p WHERE p.id = af.product_id AND p.seller_id = ?)
                """, commissionBps, recurrence == AffiliationRecurrence.ALL_CYCLES,
                Timestamp.from(now), affiliationId, sellerId);
        return changed == 1 ? find(affiliationId) : Optional.empty();
    }

    @Override
    public Optional<Affiliation> end(UUID affiliationId, UUID accountId,
                                     AffiliationEndReason reason, Instant now) {
        boolean byAffiliate = reason == AffiliationEndReason.BY_AFFILIATE;
        String owner = byAffiliate
                ? "af.affiliate_id = ?"
                : "EXISTS (SELECT 1 FROM products p WHERE p.id = af.product_id AND p.seller_id = ?)";
        int changed = jdbc.update("""
                UPDATE affiliations af
                   SET status = 'ENDED', ended_reason = ?, ended_at = ?
                 WHERE af.id = ?
                   AND af.status IN ('REQUESTED', 'APPROVED')
                   AND """ + " " + owner, reason.name(), Timestamp.from(now), affiliationId, accountId);
        return changed == 1 ? find(affiliationId) : Optional.empty();
    }

    private static MarketplaceItem marketplace(ResultSet rs) throws SQLException {
        return new MarketplaceItem(rs.getObject("product_id", UUID.class), rs.getObject("seller_id", UUID.class),
                rs.getString("product_name"), rs.getString("description"), rs.getString("seller_name"),
                Segment.valueOf(rs.getString("segment")), ChargeType.valueOf(rs.getString("charge_type")),
                rs.getLong("amount_cents"), rs.getObject("suggested_bps", Integer.class),
                rs.getInt("guarantee_days"), OfferPayoutDelay.valueOf(rs.getString("payout_delay")).days(), 60,
                rs.getTimestamp("created_at").toInstant());
    }

    private static Affiliation affiliation(ResultSet rs) throws SQLException {
        AffiliationEndReason reason = rs.getString("ended_reason") == null
                ? null : AffiliationEndReason.valueOf(rs.getString("ended_reason"));
        AffiliationStatus status = switch (rs.getString("status")) {
            case "REQUESTED" -> AffiliationStatus.PENDING;
            case "APPROVED" -> AffiliationStatus.APPROVED;
            case "ENDED" -> reason == AffiliationEndReason.FRAUD
                    ? AffiliationStatus.FRAUD_ENDED : AffiliationStatus.ENDED;
            default -> AffiliationStatus.ENDED;
        };
        return new Affiliation(rs.getObject("id", UUID.class), rs.getObject("product_id", UUID.class),
                rs.getString("product_name"), rs.getObject("seller_id", UUID.class), rs.getString("seller_name"),
                rs.getObject("affiliate_id", UUID.class), rs.getString("affiliate_name"),
                rs.getInt("commission_bps"), rs.getBoolean("recurring")
                        ? AffiliationRecurrence.ALL_CYCLES : AffiliationRecurrence.FIRST_CHARGE,
                status, reason, instant(rs, "approved_at"), instant(rs, "ended_at"),
                rs.getTimestamp("created_at").toInstant());
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
}
