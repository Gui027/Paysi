package com.paysi.ledger.jobs.adapter;

import com.paysi.ledger.jobs.port.IntegrityRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Set;

@Repository
public class JdbcIntegrityRepository implements IntegrityRepository {
    private static final Set<String> ALLOWED = Set.of(
            "v_check_unbalanced_transactions", "v_check_negative_user_buckets", "v_check_positive_debt",
            "v_check_system_sign_violation", "v_check_checkpoint_drift", "v_check_receivable_schedule",
            "v_check_refund_accumulator", "v_check_release_schedule");
    private final JdbcTemplate jdbc;

    public JdbcIntegrityRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public long violations(String view) {
        if (!ALLOWED.contains(view)) throw new IllegalArgumentException("View de integridade desconhecida");
        Long count = jdbc.queryForObject("select count(*) from " + view, Long.class);
        return count == null ? 0 : count;
    }
}
