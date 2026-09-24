package com.paysi.payment.inbox.adapter;

import com.paysi.payment.inbox.port.ChargeLookup;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcChargeLookup implements ChargeLookup {
    private final JdbcTemplate jdbc;

    public JdbcChargeLookup(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<UUID> findByProviderChargeId(String providerChargeId) {
        return jdbc.query("select id from charges where provider_charge_id=?",
                (rs, row) -> rs.getObject(1, UUID.class), providerChargeId).stream().findFirst();
    }
}
