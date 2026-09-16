package com.paysi.billing.plan.adapter;

import com.paysi.billing.plan.domain.PlanStatus;
import com.paysi.billing.plan.domain.PlatformSubscription;
import com.paysi.billing.plan.port.PlanRepository;
import com.paysi.payment.split.Plan;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class JdbcPlanRepository implements PlanRepository {
    private static final String SELECT = """
            SELECT account_id, plan, price_cents, current_period_start, current_period_end, status,
                   past_due_since, pending_plan, pending_price_cents, pending_effective_at, provider_token
              FROM platform_subscriptions
            """;

    private final JdbcTemplate jdbc;

    JdbcPlanRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<PlatformSubscription> find(UUID accountId) {
        return jdbc.query(SELECT + " WHERE account_id = ?", (rs, row) -> map(rs), accountId).stream().findFirst();
    }

    @Override
    public void schedulePendingChange(UUID accountId, Plan pendingPlan, long pendingPriceCents, Instant effectiveAt,
                                       String providerToken) {
        if (providerToken != null) {
            jdbc.update("""
                    UPDATE platform_subscriptions
                       SET pending_plan = ?, pending_price_cents = ?, pending_effective_at = ?, provider_token = ?
                     WHERE account_id = ?
                    """, pendingPlan.name(), pendingPriceCents, Timestamp.from(effectiveAt), providerToken, accountId);
        } else {
            jdbc.update("""
                    UPDATE platform_subscriptions
                       SET pending_plan = ?, pending_price_cents = ?, pending_effective_at = ?
                     WHERE account_id = ?
                    """, pendingPlan.name(), pendingPriceCents, Timestamp.from(effectiveAt), accountId);
        }
    }

    @Override
    public void insertHistory(UUID id, UUID accountId, String fromPlan, String toPlan, UUID changedBy,
                               String priceTable, Instant now) {
        jdbc.update("""
                INSERT INTO plan_changes (id, account_id, from_plan, to_plan, changed_by, price_table, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, id, accountId, fromPlan, toPlan, changedBy, priceTable, Timestamp.from(now));
    }

    @Override
    public List<PlanChangeRecord> listHistory(UUID accountId, int limit) {
        return jdbc.query("""
                SELECT id, from_plan, to_plan, price_table, created_at FROM plan_changes
                 WHERE account_id = ? ORDER BY created_at DESC LIMIT ?
                """, (rs, row) -> new PlanChangeRecord(rs.getObject("id", UUID.class), rs.getString("from_plan"),
                        rs.getString("to_plan"), rs.getString("price_table"), rs.getTimestamp("created_at").toInstant()),
                accountId, limit);
    }

    @Override
    public Optional<PlatformSubscription> claimDueRollover(Instant now) {
        return jdbc.query(SELECT + """
                 WHERE current_period_end <= ?
                 ORDER BY current_period_end
                 LIMIT 1 FOR UPDATE SKIP LOCKED
                """, (rs, row) -> map(rs), Timestamp.from(now)).stream().findFirst();
    }

    @Override
    public void applyRollover(UUID accountId, String plan, long priceCents, Instant periodStart, Instant periodEnd,
                               String status, Instant pastDueSince) {
        jdbc.update("""
                UPDATE platform_subscriptions
                   SET plan = ?, price_cents = ?, current_period_start = ?, current_period_end = ?,
                       status = ?, past_due_since = ?, pending_plan = NULL, pending_price_cents = NULL,
                       pending_effective_at = NULL
                 WHERE account_id = ?
                """, plan, priceCents, Timestamp.from(periodStart), Timestamp.from(periodEnd), status,
                timestamp(pastDueSince), accountId);
    }

    @Override
    public Optional<UUID> claimDueDowngrade(Instant now) {
        return jdbc.query("""
                SELECT account_id FROM platform_subscriptions
                 WHERE status = 'PAST_DUE' AND past_due_since IS NOT NULL AND past_due_since <= ?
                 ORDER BY past_due_since
                 LIMIT 1 FOR UPDATE SKIP LOCKED
                """, (rs, row) -> rs.getObject("account_id", UUID.class),
                Timestamp.from(now.minus(java.time.Duration.ofDays(10)))).stream().findFirst();
    }

    @Override
    public void applyDowngrade(UUID accountId, Instant periodStart, Instant periodEnd) {
        jdbc.update("""
                UPDATE platform_subscriptions
                   SET plan = 'TRANSACIONAL', price_cents = 0, status = 'DOWNGRADED', past_due_since = NULL,
                       current_period_start = ?, current_period_end = ?,
                       pending_plan = NULL, pending_price_cents = NULL, pending_effective_at = NULL
                 WHERE account_id = ?
                """, Timestamp.from(periodStart), Timestamp.from(periodEnd), accountId);
    }

    @Override
    public AccountBillingInfo billingInfo(UUID accountId) {
        return jdbc.query("SELECT full_name, email, person_type, tax_id FROM accounts WHERE id = ?",
                (rs, row) -> new AccountBillingInfo(rs.getString("full_name"), rs.getString("email"),
                        rs.getString("person_type"), rs.getString("tax_id")), accountId)
                .stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("Conta desapareceu durante cobrança de plano"));
    }

    private PlatformSubscription map(ResultSet rs) throws SQLException {
        String pendingPlan = rs.getString("pending_plan");
        Long pendingPrice = (Long) rs.getObject("pending_price_cents");
        return new PlatformSubscription(rs.getObject("account_id", UUID.class), Plan.valueOf(rs.getString("plan")),
                rs.getLong("price_cents"), rs.getTimestamp("current_period_start").toInstant(),
                rs.getTimestamp("current_period_end").toInstant(), PlanStatus.valueOf(rs.getString("status")),
                instant(rs, "past_due_since"), pendingPlan == null ? null : Plan.valueOf(pendingPlan), pendingPrice,
                instant(rs, "pending_effective_at"), rs.getString("provider_token"));
    }

    private static Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        var timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }
}
