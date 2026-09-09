package com.paysi.checkout.pub.adapter;

import com.paysi.checkout.pub.port.ProductNameLookup;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
class JdbcProductNameLookup implements ProductNameLookup {
    private final JdbcTemplate jdbc;

    JdbcProductNameLookup(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<String> findActiveProductName(UUID productId) {
        return jdbc.query("SELECT name FROM products WHERE id = ? AND archived_at IS NULL",
                (rs, row) -> rs.getString("name"), productId).stream().findFirst();
    }
}
