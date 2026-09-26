package com.paysi.reports.adapter;

import com.paysi.reports.app.ReportsModels.ReportFilter;
import com.paysi.reports.port.ReportsQueryRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** Leituras dos relatórios do vendedor. Toda soma em dinheiro é feita aqui, no banco. */
@Repository
class JdbcReportsQueryRepository implements ReportsQueryRepository {
    private static final String ZONE = "America/Sao_Paulo";
    private static final String CHARGES = """
              FROM charges c
              JOIN orders o ON o.id = c.order_id
              JOIN offers f ON f.id = o.offer_id
              JOIN products p ON p.id = f.product_id
              JOIN buyers b ON b.id = o.buyer_id
            """;
    private static final String NET = """
            (c.seller_amount_cents - COALESCE((SELECT SUM(r.seller_cents) FROM refunds r
                                                WHERE r.charge_id = c.id AND r.status = 'SUCCEEDED'), 0))
            """;

    private final JdbcTemplate jdbc;

    JdbcReportsQueryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private record Where(StringBuilder sql, List<Object> params) { }

    private static Where where(String base, UUID sellerId) {
        List<Object> params = new ArrayList<>();
        params.add(sellerId);
        return new Where(new StringBuilder(base), params);
    }

    private static void period(Where where, String column, LocalDate from, LocalDate to) {
        if (from != null) {
            where.sql().append(" AND (").append(column).append(" AT TIME ZONE '").append(ZONE).append("')::date >= ?");
            where.params().add(java.sql.Date.valueOf(from));
        }
        if (to != null) {
            where.sql().append(" AND (").append(column).append(" AT TIME ZONE '").append(ZONE).append("')::date <= ?");
            where.params().add(java.sql.Date.valueOf(to));
        }
    }

    private static void search(Where where, ReportFilter filter) {
        if (filter.productId() != null) {
            where.sql().append(" AND p.id = ?");
            where.params().add(filter.productId());
        }
        String query = filter.query();
        if (query != null && !query.isBlank()) {
            String like = "%" + query.strip().toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            where.sql().append(" AND (lower(b.name) LIKE ? ESCAPE '\\' OR lower(b.email::text) LIKE ? ESCAPE '\\'"
                    + " OR lower(p.name) LIKE ? ESCAPE '\\')");
            where.params().add(like);
            where.params().add(like);
            where.params().add(like);
        }
    }

    @Override
    public List<List<Object>> revenueByProduct(UUID sellerId, LocalDate from, LocalDate to) {
        Where where = where(" WHERE p.seller_id = ? AND c.status IN ('PAID', 'PARTIALLY_REFUNDED')", sellerId);
        period(where, "c.created_at", from, to);
        return jdbc.query("SELECT p.name AS label, COUNT(*) AS sales, COALESCE(SUM(" + NET + "), 0) AS total " + CHARGES
                + where.sql() + " GROUP BY p.id, p.name ORDER BY total DESC, p.name LIMIT 500",
                (rs, row) -> cells(rs.getString("label"), rs.getLong("sales"), rs.getLong("total")), where.params().toArray());
    }

    @Override
    public List<List<Object>> revenueByAffiliate(UUID sellerId, LocalDate from, LocalDate to) {
        Where where = where(" WHERE p.seller_id = ? AND c.status IN ('PAID', 'PARTIALLY_REFUNDED')", sellerId);
        period(where, "c.created_at", from, to);
        return jdbc.query("SELECT a.full_name AS label, COUNT(*) AS sales, COALESCE(SUM(c.affiliate_fee_cents), 0) AS total "
                + CHARGES + " JOIN affiliations af ON af.id = o.affiliation_id JOIN accounts a ON a.id = af.affiliate_id"
                + where.sql() + " GROUP BY a.id, a.full_name ORDER BY total DESC, a.full_name LIMIT 500",
                (rs, row) -> cells(rs.getString("label"), rs.getLong("sales"), rs.getLong("total")), where.params().toArray());
    }

    @Override
    public List<List<Object>> abandoned(UUID sellerId, ReportFilter filter) {
        Where where = where(" WHERE p.seller_id = ? AND c.status IN ('PENDING', 'EXPIRED', 'FAILED') AND c.cycle_number = 1", sellerId);
        period(where, "c.created_at", filter.from(), filter.to());
        search(where, filter);
        return jdbc.query("""
                SELECT c.created_at, p.name AS product_name, b.name AS buyer_name, b.email::text AS buyer_email,
                       o.buyer_phone, c.amount_cents, c.status
                """ + CHARGES + where.sql() + " ORDER BY c.created_at DESC, c.id DESC LIMIT 5000",
                (rs, row) -> cells(instant(rs, "created_at"), rs.getString("product_name"), rs.getString("buyer_name"),
                        rs.getString("buyer_email"), rs.getString("buyer_phone"), rs.getLong("amount_cents"),
                        rs.getString("status")), where.params().toArray());
    }

    @Override
    public List<List<Object>> receivables(UUID sellerId, LocalDate from, LocalDate to, List<String> methods) {
        if (methods.isEmpty()) return List.of();
        Where where = where(" WHERE s.account_id = ? AND s.released_at IS NULL AND o.method IN ("
                + String.join(", ", Collections.nCopies(methods.size(), "?")) + ")", sellerId);
        where.params().addAll(methods);
        period(where, "s.release_at", from, to);
        return jdbc.query("SELECT (s.release_at AT TIME ZONE '" + ZONE + "')::date AS day, SUM(s.amount_cents) AS total "
                + """
                  FROM ledger_release_schedule s
                  JOIN ledger_entries e ON e.id = s.entry_id
                  JOIN ledger_transactions t ON t.id = e.transaction_id AND t.reference_type = 'CHARGE'
                  JOIN charges c ON c.id::text = t.reference_id
                  JOIN orders o ON o.id = c.order_id
                """ + where.sql() + " GROUP BY day ORDER BY day",
                (rs, row) -> cells(rs.getDate("day").toLocalDate().toString(), rs.getLong("total")), where.params().toArray());
    }

    @Override
    public List<List<Object>> canceledSubscriptions(UUID sellerId, ReportFilter filter) {
        Where where = where(" WHERE p.seller_id = ? AND s.status = 'CANCELED' AND s.canceled_at IS NOT NULL", sellerId);
        period(where, "s.canceled_at", filter.from(), filter.to());
        search(where, filter);
        return jdbc.query("""
                SELECT s.canceled_at, p.name AS product_name, f.name AS offer_name, f.cycle,
                       b.name AS buyer_name, b.email::text AS buyer_email
                  FROM subscriptions s
                  JOIN offers f ON f.id = s.offer_id
                  JOIN products p ON p.id = f.product_id
                  JOIN orders o ON o.id = s.order_id
                  JOIN buyers b ON b.id = o.buyer_id
                """ + where.sql() + " ORDER BY s.canceled_at DESC, s.id DESC LIMIT 5000",
                (rs, row) -> cells(instant(rs, "canceled_at"), rs.getString("product_name"),
                        plan(rs.getString("offer_name"), rs.getString("cycle")), rs.getString("buyer_name"),
                        rs.getString("buyer_email")), where.params().toArray());
    }

    private static String plan(String name, String cycle) {
        if (name != null && !name.isBlank()) return name;
        return switch (cycle == null ? "" : cycle) {
            case "QUARTERLY" -> "Plano Trimestral";
            case "SEMIANNUAL" -> "Plano Semestral";
            case "ANNUAL" -> "Plano Anual";
            default -> "Plano Mensal";
        };
    }

    private static String instant(ResultSet rs, String column) throws SQLException {
        var value = rs.getTimestamp(column);
        return value == null ? "" : value.toInstant().toString();
    }

    private static List<Object> cells(Object... values) {
        List<Object> row = new ArrayList<>(values.length);
        for (Object value : values) row.add(value == null ? "" : value);
        return row;
    }
}
