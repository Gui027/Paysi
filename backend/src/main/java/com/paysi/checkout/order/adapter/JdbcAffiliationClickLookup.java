package com.paysi.checkout.order.adapter;

import com.paysi.checkout.order.port.AffiliationClickLookup;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
class JdbcAffiliationClickLookup implements AffiliationClickLookup {
    private final JdbcTemplate jdbc;

    JdbcAffiliationClickLookup(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Attribution> findLastClick(String visitorKey, UUID productId) {
        return jdbc.query("""
                SELECT a.id, a.commission_bps
                  FROM affiliate_clicks c
                  JOIN affiliations a ON a.id = c.affiliation_id
                 WHERE c.visitor_key = ? AND c.product_id = ?
                   AND c.expires_at > now()
                   AND a.status = 'APPROVED'
                 ORDER BY c.created_at DESC
                 LIMIT 1
                """, (rs, row) -> new Attribution(rs.getObject("id", UUID.class),
                        rs.getInt("commission_bps")), visitorKey, productId)
                .stream().findFirst();
    }
}
