package com.paysi.fiscal.adapter;

import com.paysi.fiscal.domain.FiscalProfile;
import com.paysi.fiscal.port.FiscalProfileRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
class JdbcFiscalProfileRepository implements FiscalProfileRepository {
    private final JdbcTemplate jdbc;

    JdbcFiscalProfileRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<FiscalProfile> findByAccount(UUID accountId) {
        return jdbc.query("""
                SELECT account_id, municipality_code, municipal_reg, service_code, iss_bps, tax_regime,
                       credential_ref, validated_at
                  FROM fiscal_profiles
                 WHERE account_id = ?
                """, (rs, row) -> new FiscalProfile(rs.getObject("account_id", UUID.class),
                        rs.getString("municipality_code"), rs.getString("municipal_reg"),
                        rs.getString("service_code"), rs.getInt("iss_bps"), rs.getString("tax_regime"),
                        rs.getString("credential_ref"),
                        rs.getTimestamp("validated_at") == null ? null : rs.getTimestamp("validated_at").toInstant()),
                accountId).stream().findFirst();
    }

    @Override
    public void upsert(UUID accountId, String municipalityCode, String municipalRegistration, String serviceItem,
                        int taxBps, String taxRegime, String credentialRef) {
        jdbc.update("""
                INSERT INTO fiscal_profiles
                  (account_id, municipality_code, municipal_reg, service_code, iss_bps, tax_regime,
                   credential_ref, validated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, NULL)
                ON CONFLICT (account_id) DO UPDATE SET
                  municipality_code = EXCLUDED.municipality_code,
                  municipal_reg = EXCLUDED.municipal_reg,
                  service_code = EXCLUDED.service_code,
                  iss_bps = EXCLUDED.iss_bps,
                  tax_regime = EXCLUDED.tax_regime,
                  credential_ref = EXCLUDED.credential_ref,
                  validated_at = NULL
                """, accountId, municipalityCode, municipalRegistration, serviceItem, taxBps, taxRegime,
                credentialRef);
    }

    @Override
    public void markValidated(UUID accountId, Instant validatedAt) {
        jdbc.update("UPDATE fiscal_profiles SET validated_at = ? WHERE account_id = ?",
                Timestamp.from(validatedAt), accountId);
    }
}
