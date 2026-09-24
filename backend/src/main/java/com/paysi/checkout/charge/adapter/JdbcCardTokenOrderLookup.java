package com.paysi.checkout.charge.adapter;

import com.paysi.checkout.charge.port.CardTokenOrderLookup;
import com.paysi.payment.provider.ProviderBuyer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcCardTokenOrderLookup implements CardTokenOrderLookup {
    private final JdbcTemplate jdbc;

    public JdbcCardTokenOrderLookup(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<ProviderBuyer> findBuyerOfPendingCardOrder(UUID orderId) {
        return jdbc.query("""
                select b.name,b.email::text,b.person_type,b.tax_id
                  from orders o join buyers b on b.id=o.buyer_id
                 where o.id=? and o.method='CARD' and o.status='PENDING'
                """, (rs, row) -> new ProviderBuyer(rs.getString(1), rs.getString(2), rs.getString(3),
                rs.getString(4)), orderId).stream().findFirst();
    }
}
