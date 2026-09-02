package com.paysi.catalog.offer.adapter;

import com.paysi.catalog.offer.port.PublicationRequirementRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
class JdbcPublicationRequirementRepository implements PublicationRequirementRepository {
    private final JdbcTemplate jdbc;

    JdbcPublicationRequirementRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean hasValidatedFiscalProfile(UUID sellerId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM fiscal_profiles
                     WHERE account_id = ? AND validated_at IS NOT NULL
                )
                """, Boolean.class, sellerId));
    }
}
