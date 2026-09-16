package com.paysi.affiliate.adapter;

import com.paysi.affiliate.port.AffiliateAttributionRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class JdbcAffiliateAttributionRepository implements AffiliateAttributionRepository {
    private final JdbcTemplate jdbc;

    JdbcAffiliateAttributionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<UUID> findApprovedAffiliation(UUID productId, UUID affiliateId) {
        return jdbc.query("""
                SELECT id FROM affiliations
                 WHERE product_id = ? AND affiliate_id = ? AND status = 'APPROVED'
                """, (rs, row) -> rs.getObject("id", UUID.class), productId, affiliateId).stream().findFirst();
    }

    @Override
    public void recordClick(UUID id, UUID affiliationId, UUID productId, String visitorKey, String ip,
                             Instant now, Instant expiresAt) {
        jdbc.update("""
                INSERT INTO affiliate_clicks (id, affiliation_id, product_id, visitor_key, ip, created_at, expires_at)
                VALUES (?, ?, ?, ?, cast(? as inet), ?, ?)
                """, id, affiliationId, productId, visitorKey, ip, Timestamp.from(now), Timestamp.from(expiresAt));
    }

    @Override
    public Optional<Attribution> resolveAttribution(UUID productId, String visitorKey, Instant now) {
        return jdbc.query("""
                SELECT a.id AS affiliation_id, a.affiliate_id, a.commission_bps, a.recurring
                  FROM affiliate_clicks c
                  JOIN affiliations a ON a.id = c.affiliation_id
                 WHERE c.product_id = ? AND c.visitor_key = ? AND c.expires_at > ? AND a.status = 'APPROVED'
                 ORDER BY c.created_at DESC
                 LIMIT 1
                """, (rs, row) -> new Attribution(rs.getObject("affiliation_id", UUID.class),
                        rs.getObject("affiliate_id", UUID.class), rs.getInt("commission_bps"),
                        rs.getBoolean("recurring")),
                productId, visitorKey, Timestamp.from(now)).stream().findFirst();
    }

    @Override
    public List<LinkStats> linkStats(UUID affiliateId) {
        return jdbc.query("""
                SELECT a.id AS affiliation_id, a.product_id, p.name AS product_name,
                       (SELECT o.slug FROM offers o WHERE o.product_id = p.id AND o.status = 'PUBLISHED'
                         ORDER BY o.created_at LIMIT 1) AS offer_slug,
                       (SELECT count(*) FROM affiliate_clicks c WHERE c.affiliation_id = a.id) AS clicks,
                       (SELECT count(*) FROM orders o WHERE o.affiliation_id = a.id) AS orders
                  FROM affiliations a
                  JOIN products p ON p.id = a.product_id
                 WHERE a.affiliate_id = ? AND a.status = 'APPROVED'
                 ORDER BY a.created_at DESC
                """, (rs, row) -> new LinkStats(rs.getObject("affiliation_id", UUID.class),
                        rs.getObject("product_id", UUID.class), rs.getString("product_name"),
                        rs.getString("offer_slug"), rs.getLong("clicks"), rs.getLong("orders")), affiliateId);
    }
}
