package com.paysi.payment.provider.adapter;

import com.paysi.payment.provider.WalletLookup;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
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
        // Não usar .stream().findFirst(): a coluna é nula para toda conta sem subconta ainda, e
        // Optional.of(null) (o que findFirst faz por baixo) lança NullPointerException.
        List<String> rows = jdbc.query("select provider_account_id from accounts where id = ?",
                (rs, row) -> rs.getString(1), accountId);
        if (rows.isEmpty()) return Optional.empty();
        String value = rows.get(0);
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value);
    }
}
