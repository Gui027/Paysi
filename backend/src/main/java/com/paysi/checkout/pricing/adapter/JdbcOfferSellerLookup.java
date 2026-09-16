package com.paysi.checkout.pricing.adapter;

import com.paysi.checkout.pricing.port.OfferSellerLookup;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
class JdbcOfferSellerLookup implements OfferSellerLookup {
    private final JdbcTemplate jdbc;

    JdbcOfferSellerLookup(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<UUID> findSellerId(UUID productId) {
        return jdbc.query("SELECT seller_id FROM products WHERE id = ? AND archived_at IS NULL",
                (rs, row) -> rs.getObject("seller_id", UUID.class), productId).stream().findFirst();
    }
}
