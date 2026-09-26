package com.paysi.sales.adapter;

import com.paysi.sales.app.SalesModels.Buyer;
import com.paysi.sales.app.SubscriptionsModels.SubscriptionDetail;
import com.paysi.sales.app.SubscriptionsModels.SubscriptionPayment;
import com.paysi.sales.app.SubscriptionsModels.SubscriptionRow;
import com.paysi.sales.app.SubscriptionsModels.SubscriptionsFilter;
import com.paysi.sales.app.SubscriptionsModels.SubscriptionsSummary;
import com.paysi.sales.port.SubscriptionsQueryRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;
import java.util.UUID;

/** Leitura das assinaturas do vendedor: uma linha por assinatura, com o líquido da cobrança mais recente. */
@Repository
class JdbcSubscriptionsQueryRepository implements SubscriptionsQueryRepository {
    private static final String FROM = """
              FROM subscriptions s
              JOIN offers f ON f.id = s.offer_id
              JOIN products p ON p.id = f.product_id
              JOIN orders o ON o.id = s.order_id
              JOIN buyers b ON b.id = o.buyer_id
            """;

    /** Líquido do vendedor na cobrança mais recente (a paga tem prioridade sobre uma pendente). */
    private static final String NET = """
            (SELECT c.seller_amount_cents FROM charges c WHERE c.subscription_id = s.id
              ORDER BY (c.status IN ('PAID', 'PARTIALLY_REFUNDED')) DESC, c.cycle_number DESC LIMIT 1)
            """;

    private static final String MONTHS = """
            (CASE f.cycle WHEN 'QUARTERLY' THEN 3 WHEN 'SEMIANNUAL' THEN 6 WHEN 'ANNUAL' THEN 12 ELSE 1 END)
            """;

    private final JdbcTemplate jdbc;

    JdbcSubscriptionsQueryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private record Where(String sql, List<Object> params) { }

    @Override
    public List<SubscriptionRow> list(UUID sellerId, SubscriptionsFilter filter, int limit, int offset) {
        Where where = where(sellerId, filter, true);
        List<Object> params = new ArrayList<>(where.params());
        params.add(limit);
        params.add(offset);
        return jdbc.query("""
                SELECT s.id, s.created_at, s.status, s.canceled_at, s.next_charge_at,
                       p.id AS product_id, p.name AS product_name, f.name AS offer_name, f.cycle,
                       b.name AS buyer_name, b.email::text AS buyer_email, %s AS net_cents
                """.formatted(NET) + FROM + where.sql() + " ORDER BY s.created_at DESC, s.id DESC LIMIT ? OFFSET ?",
                (rs, row) -> row(rs), params.toArray());
    }

    @Override
    public long count(UUID sellerId, SubscriptionsFilter filter) {
        Where where = where(sellerId, filter, true);
        Long total = jdbc.queryForObject("SELECT COUNT(*) " + FROM + where.sql(), Long.class, where.params().toArray());
        return total == null ? 0 : total;
    }

    @Override
    public SubscriptionsSummary summarize(UUID sellerId, SubscriptionsFilter filter) {
        Where where = where(sellerId, filter, false);
        return jdbc.queryForObject("""
                SELECT COUNT(*) FILTER (WHERE s.status IN ('ACTIVE', 'TRIAL')) AS active_count,
                       COALESCE(SUM(CASE WHEN s.status = 'ACTIVE' THEN COALESCE(%s, 0) / %s ELSE 0 END), 0) AS recurring
                """.formatted(NET, MONTHS) + FROM + where.sql(),
                (rs, row) -> new SubscriptionsSummary(rs.getLong("active_count"), rs.getLong("recurring")),
                where.params().toArray());
    }

    @Override
    public Optional<SubscriptionDetail> find(UUID sellerId, UUID subscriptionId) {
        Optional<SubscriptionDetail> found = jdbc.query("""
                SELECT s.id, s.created_at, s.status, s.canceled_at, s.next_charge_at, s.trial_ends_at,
                       p.id AS product_id, p.name AS product_name, f.name AS offer_name, f.cycle,
                       o.method, o.installments, o.buyer_phone, o.buyer_ip,
                       b.name AS buyer_name, b.email::text AS buyer_email, b.tax_id, b.person_type,
                       %s AS net_cents,
                       (SELECT COUNT(*) FROM charges c WHERE c.subscription_id = s.id
                           AND c.status IN ('PAID', 'PARTIALLY_REFUNDED', 'REFUNDED')) AS approved_charges
                """.formatted(NET) + FROM + " WHERE p.seller_id = ? AND s.id = ?",
                (rs, row) -> detail(rs), sellerId, subscriptionId).stream().findFirst();
        return found.map(detail -> new SubscriptionDetail(detail.id(), detail.code(), detail.status(),
                detail.cancelPending(), detail.type(), detail.createdAt(), detail.accessUntil(),
                detail.trialEndsAt(), detail.nextChargeAt(), detail.canceledAt(), detail.productName(),
                detail.productId(), detail.offerName(), detail.cycle(), detail.netCents(), detail.installments(),
                detail.method(), detail.approvedCharges(), detail.buyer(), payments(subscriptionId),
                detail.canCancel()));
    }

    private List<SubscriptionPayment> payments(UUID subscriptionId) {
        return jdbc.query("""
                SELECT c.id, c.cycle_number, c.created_at, c.paid_at, c.status, c.seller_amount_cents
                  FROM charges c WHERE c.subscription_id = ?
                 ORDER BY c.cycle_number DESC, c.created_at DESC
                """, (rs, row) -> new SubscriptionPayment(rs.getObject("id", UUID.class), rs.getInt("cycle_number"),
                instant(rs, "created_at"), instant(rs, "paid_at"), rs.getString("status"),
                rs.getLong("seller_amount_cents")), subscriptionId);
    }

    // ---------- filtros ----------

    private static Where where(UUID sellerId, SubscriptionsFilter filter, boolean withTab) {
        StringBuilder sql = new StringBuilder(" WHERE p.seller_id = ?");
        List<Object> params = new ArrayList<>();
        params.add(sellerId);
        if (withTab) {
            if ("active".equals(filter.tab())) sql.append(" AND s.status <> 'CANCELED'");
            else if ("canceled".equals(filter.tab())) sql.append(" AND s.status = 'CANCELED'");
        }
        if (!filter.statuses().isEmpty()) {
            sql.append(" AND s.status IN (").append(String.join(", ", java.util.Collections.nCopies(filter.statuses().size(), "?"))).append(")");
            params.addAll(new TreeSet<>(filter.statuses()));
        }
        if (filter.cycle() != null) {
            sql.append(" AND f.cycle = ?");
            params.add(filter.cycle());
        }
        if (filter.method() != null) {
            sql.append(" AND o.method = ?");
            params.add(filter.method());
        }
        if (filter.productId() != null) {
            sql.append(" AND p.id = ?");
            params.add(filter.productId());
        }
        if (filter.from() != null) {
            sql.append(" AND (s.created_at AT TIME ZONE 'America/Sao_Paulo')::date >= ?");
            params.add(java.sql.Date.valueOf(filter.from()));
        }
        if (filter.to() != null) {
            sql.append(" AND (s.created_at AT TIME ZONE 'America/Sao_Paulo')::date <= ?");
            params.add(java.sql.Date.valueOf(filter.to()));
        }
        String query = filter.query();
        if (query != null && !query.isBlank()) {
            String text = query.strip();
            String like = "%" + text.toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            sql.append(" AND (lower(b.name) LIKE ? ESCAPE '\\' OR lower(b.email::text) LIKE ? ESCAPE '\\'"
                    + " OR lower(p.name) LIKE ? ESCAPE '\\' OR upper(left(s.id::text, 7)) = ?");
            params.add(like);
            params.add(like);
            params.add(like);
            params.add(text.toUpperCase());
            String digits = text.replaceAll("\\D", "");
            if (digits.length() >= 3) {
                sql.append(" OR b.tax_id LIKE ?");
                params.add("%" + digits + "%");
            }
            sql.append(")");
        }
        return new Where(sql.toString(), params);
    }

    // ---------- mapeamento ----------

    private static String code(UUID id) {
        return id.toString().substring(0, 7).toUpperCase();
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static SubscriptionRow row(ResultSet rs) throws SQLException {
        UUID id = rs.getObject("id", UUID.class);
        String status = rs.getString("status");
        boolean cancelPending = rs.getTimestamp("canceled_at") != null && !"CANCELED".equals(status);
        return new SubscriptionRow(id, code(id), instant(rs, "created_at"), status, cancelPending,
                rs.getString("product_name"), rs.getObject("product_id", UUID.class), rs.getString("offer_name"),
                rs.getString("cycle"), rs.getString("buyer_name"), rs.getString("buyer_email"),
                nullableLong(rs, "net_cents"), instant(rs, "next_charge_at"));
    }

    private static SubscriptionDetail detail(ResultSet rs) throws SQLException {
        UUID id = rs.getObject("id", UUID.class);
        String status = rs.getString("status");
        boolean cancelPending = rs.getTimestamp("canceled_at") != null && !"CANCELED".equals(status);
        Instant nextCharge = instant(rs, "next_charge_at");
        Instant trialEnds = instant(rs, "trial_ends_at");
        Instant accessUntil = "CANCELED".equals(status) ? null : (nextCharge != null ? nextCharge : trialEnds);
        return new SubscriptionDetail(id, code(id), status, cancelPending, "PRODUCER", instant(rs, "created_at"),
                accessUntil, trialEnds, nextCharge, instant(rs, "canceled_at"), rs.getString("product_name"),
                rs.getObject("product_id", UUID.class), rs.getString("offer_name"), rs.getString("cycle"),
                nullableLong(rs, "net_cents"), rs.getInt("installments"), rs.getString("method"),
                rs.getInt("approved_charges"),
                new Buyer(rs.getString("buyer_name"), rs.getString("buyer_email"), rs.getString("buyer_phone"),
                        rs.getString("tax_id"), rs.getString("person_type"), rs.getString("buyer_ip")),
                List.of(), !"CANCELED".equals(status) && !cancelPending);
    }
}
