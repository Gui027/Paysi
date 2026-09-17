package com.paysi.ledger.adapter;

import com.paysi.ledger.port.ChargeSaleRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
class JdbcChargeSaleRepository implements ChargeSaleRepository {
    private final JdbcTemplate jdbc;

    JdbcChargeSaleRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<ChargeSale> findChargeSale(UUID chargeId) {
        return jdbc.query("""
                SELECT p.seller_id, c.seller_amount_cents, c.platform_fee_cents, c.affiliate_fee_cents,
                       a.affiliate_id, of.guarantee_days
                  FROM charges c
                  JOIN orders o ON o.id = c.order_id
                  JOIN offers of ON of.id = o.offer_id
                  JOIN products p ON p.id = of.product_id
                  LEFT JOIN affiliations a ON a.id = o.affiliation_id
                 WHERE c.id = ?
                """, (rs, row) -> new ChargeSale(rs.getObject("seller_id", UUID.class),
                        rs.getLong("seller_amount_cents"), rs.getLong("platform_fee_cents"),
                        rs.getObject("affiliate_id", UUID.class), rs.getLong("affiliate_fee_cents"),
                        rs.getInt("guarantee_days")), chargeId).stream().findFirst();
    }
}
