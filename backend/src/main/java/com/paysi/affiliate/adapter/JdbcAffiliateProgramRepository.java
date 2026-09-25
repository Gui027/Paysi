package com.paysi.affiliate.adapter;

import com.paysi.affiliate.domain.AffiliateProgram;
import com.paysi.affiliate.domain.AffiliationRecurrence;
import com.paysi.affiliate.port.AffiliateProgramRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
class JdbcAffiliateProgramRepository implements AffiliateProgramRepository {
    private final JdbcTemplate jdbc;

    JdbcAffiliateProgramRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<AffiliateProgram> find(UUID productId) {
        return jdbc.query("""
                SELECT product_id, commission_bps, recurrence, auto_approve, support_email, description
                  FROM affiliate_program_settings WHERE product_id = ?
                """, (rs, row) -> new AffiliateProgram(rs.getObject("product_id", UUID.class),
                rs.getInt("commission_bps"), AffiliationRecurrence.valueOf(rs.getString("recurrence")),
                rs.getBoolean("auto_approve"), rs.getString("support_email"), rs.getString("description")),
                productId).stream().findFirst();
    }

    @Override
    public void save(AffiliateProgram program) {
        jdbc.update("""
                INSERT INTO affiliate_program_settings
                    (product_id, commission_bps, recurrence, auto_approve, support_email, description, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, now())
                ON CONFLICT (product_id) DO UPDATE
                   SET commission_bps = EXCLUDED.commission_bps, recurrence = EXCLUDED.recurrence,
                       auto_approve = EXCLUDED.auto_approve, support_email = EXCLUDED.support_email,
                       description = EXCLUDED.description, updated_at = now()
                """, program.productId(), program.commissionBps(), program.recurrence().name(),
                program.autoApprove(), program.supportEmail(), program.description());
    }
}
