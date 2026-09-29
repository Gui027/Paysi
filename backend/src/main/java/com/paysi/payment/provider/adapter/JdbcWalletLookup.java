package com.paysi.payment.provider.adapter;

import com.paysi.payment.provider.WalletLookup;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
class JdbcWalletLookup implements WalletLookup {
    private final JdbcTemplate jdbc;

    JdbcWalletLookup(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<String> walletId(UUID accountId) {
        return jdbc.query("select provider_account_id from accounts where id = ?", (rs, row) -> rs.getString(1), accountId)
                .stream().findFirst().filter(value -> value != null && !value.isBlank());
    }
}
