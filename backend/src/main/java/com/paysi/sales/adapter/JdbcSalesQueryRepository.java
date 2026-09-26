package com.paysi.sales.adapter;

import com.paysi.sales.app.SalesModels.Amounts;
import com.paysi.sales.app.SalesModels.Buyer;
import com.paysi.sales.app.SalesModels.Participant;
import com.paysi.sales.app.SalesModels.RefundFilter;
import com.paysi.sales.app.SalesModels.RefundRow;
import com.paysi.sales.app.SalesModels.SaleDetail;
import com.paysi.sales.app.SalesModels.SaleRow;
import com.paysi.sales.app.SalesModels.SalesFilter;
import com.paysi.sales.app.SalesModels.SalesSummary;
import com.paysi.sales.port.SalesQueryRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Leitura das vendas do vendedor (uma venda = uma cobrança). O valor líquido já desconta o que foi
 * reembolsado ao vendedor, e todas as contas de dinheiro nascem aqui, no banco.
 */
@Repository
class JdbcSalesQueryRepository implements SalesQueryRepository {
    private static final String FROM = """
              FROM charges c
              JOIN orders o ON o.id = c.order_id
              JOIN offers f ON f.id = o.offer_id
              JOIN products p ON p.id = f.product_id
              JOIN buyers b ON b.id = o.buyer_id
            """;

    /** Líquido do vendedor: o que ele recebe na cobrança menos as devoluções já concluídas. */
    private static final String NET = """
            (c.seller_amount_cents - COALESCE((SELECT SUM(r.seller_cents) FROM refunds r
                                                WHERE r.charge_id = c.id AND r.status = 'SUCCEEDED'), 0))
            """;

    private final JdbcTemplate jdbc;

    JdbcSalesQueryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private record Where(String sql, List<Object> params) { }

    @Override
    public List<SaleRow> list(UUID sellerId, SalesFilter filter, int limit, int offset) {
        Where where = where(sellerId, filter);
        List<Object> params = new ArrayList<>(where.params());
        params.add(limit);
        params.add(offset);
        return jdbc.query("""
                SELECT c.id, c.created_at, c.confirmed_at, c.status, o.method, o.installments,
                       p.id AS product_id, p.name AS product_name, f.name AS offer_name,
                       b.name AS buyer_name, b.email::text AS buyer_email, %s AS net_cents
                """.formatted(NET) + FROM + where.sql()
                + " ORDER BY c.created_at DESC, c.id DESC LIMIT ? OFFSET ?", (rs, row) -> saleRow(rs), params.toArray());
    }

    @Override
    public SalesSummary summarize(UUID sellerId, SalesFilter filter) {
        Where where = where(sellerId, filter);
        return jdbc.queryForObject("""
                SELECT COUNT(*) AS total,
                       COALESCE(SUM(CASE WHEN c.status IN ('PAID', 'PARTIALLY_REFUNDED') THEN %s ELSE 0 END), 0) AS net
                """.formatted(NET) + FROM + where.sql(),
                (rs, row) -> new SalesSummary(rs.getLong("total"), rs.getLong("net")), where.params().toArray());
    }

    @Override
    public Optional<SaleDetail> find(UUID sellerId, UUID chargeId) {
        Optional<SaleDetail> detail = jdbc.query("""
                SELECT c.id, c.created_at, c.confirmed_at, c.status, c.cycle_number, c.subscription_id,
                       c.amount_cents, c.platform_fee_cents, c.affiliate_fee_cents, c.seller_amount_cents,
                       c.refunded_cents, o.method, o.installments, o.discount_cents, o.external_ref,
                       o.buyer_phone, o.buyer_ip, p.id AS product_id, p.name AS product_name,
                       f.name AS offer_name, f.guarantee_days, b.name AS buyer_name, b.email::text AS buyer_email,
                       b.tax_id, b.person_type, cp.code::text AS coupon_code, seller.full_name AS seller_name,
                       aff.full_name AS affiliate_name, %s AS net_cents
                """.formatted(NET) + FROM + """
                  JOIN accounts seller ON seller.id = p.seller_id
                  LEFT JOIN coupons cp ON cp.id = o.coupon_id
                  LEFT JOIN affiliations af ON af.id = o.affiliation_id
                  LEFT JOIN accounts aff ON aff.id = af.affiliate_id
                 WHERE p.seller_id = ? AND c.id = ?
                """, (rs, row) -> detail(rs), sellerId, chargeId).stream().findFirst();
        return detail.map(found -> withRefunds(sellerId, found));
    }

    private SaleDetail withRefunds(UUID sellerId, SaleDetail found) {
        List<RefundRow> refunds = jdbc.query(refundSelect() + " WHERE p.seller_id = ? AND r.charge_id = ?"
                + " ORDER BY r.created_at DESC, r.id DESC", (rs, row) -> refundRow(rs), sellerId, found.id());
        return new SaleDetail(found.id(), found.code(), found.status(), found.type(), found.productName(),
                found.productId(), found.offerName(), found.method(), found.installments(), found.createdAt(),
                found.approvedAt(), found.availableAt(), found.reference(), found.cycleNumber(),
                found.subscriptionId(), found.couponCode(), found.buyer(), found.amounts(), found.split(),
                found.payoutState(), found.canRefund(), refunds);
    }

    @Override
    public List<RefundRow> listRefunds(UUID sellerId, RefundFilter filter, int limit, int offset) {
        Where where = refundWhere(sellerId, filter);
        List<Object> params = new ArrayList<>(where.params());
        params.add(limit);
        params.add(offset);
        return jdbc.query(refundSelect() + where.sql() + " ORDER BY r.created_at DESC, r.id DESC LIMIT ? OFFSET ?",
                (rs, row) -> refundRow(rs), params.toArray());
    }

    @Override
    public long countRefunds(UUID sellerId, RefundFilter filter) {
        Where where = refundWhere(sellerId, filter);
        Long total = jdbc.queryForObject("SELECT COUNT(*) " + refundFrom() + where.sql(), Long.class,
                where.params().toArray());
        return total == null ? 0 : total;
    }

    // ---------- filtros ----------

    private static Where where(UUID sellerId, SalesFilter filter) {
        StringBuilder sql = new StringBuilder(" WHERE p.seller_id = ?");
        List<Object> params = new ArrayList<>();
        params.add(sellerId);
        if (filter.approvedOnly()) {
            sql.append(" AND c.status IN ('PAID', 'PARTIALLY_REFUNDED')");
        } else if (!filter.statuses().isEmpty()) {
            sql.append(" AND c.status IN (").append(placeholders(filter.statuses().size())).append(")");
            params.addAll(new TreeSet<>(filter.statuses()));
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
            sql.append(" AND (c.created_at AT TIME ZONE 'America/Sao_Paulo')::date >= ?");
            params.add(java.sql.Date.valueOf(filter.from()));
        }
        if (filter.to() != null) {
            sql.append(" AND (c.created_at AT TIME ZONE 'America/Sao_Paulo')::date <= ?");
            params.add(java.sql.Date.valueOf(filter.to()));
        }
        appendSearch(sql, params, filter.query(), "c");
        return new Where(sql.toString(), params);
    }

    private static Where refundWhere(UUID sellerId, RefundFilter filter) {
        StringBuilder sql = new StringBuilder(" WHERE p.seller_id = ?");
        List<Object> params = new ArrayList<>();
        params.add(sellerId);
        if (!filter.statuses().isEmpty()) {
            sql.append(" AND r.status IN (").append(placeholders(filter.statuses().size())).append(")");
            params.addAll(new TreeSet<>(filter.statuses()));
        }
        if (!filter.origins().isEmpty()) {
            sql.append(" AND r.requested_by IN (").append(placeholders(filter.origins().size())).append(")");
            params.addAll(new TreeSet<>(filter.origins()));
        }
        if (filter.from() != null) {
            sql.append(" AND (r.created_at AT TIME ZONE 'America/Sao_Paulo')::date >= ?");
            params.add(java.sql.Date.valueOf(filter.from()));
        }
        if (filter.to() != null) {
            sql.append(" AND (r.created_at AT TIME ZONE 'America/Sao_Paulo')::date <= ?");
            params.add(java.sql.Date.valueOf(filter.to()));
        }
        appendSearch(sql, params, filter.query(), "c");
        return new Where(sql.toString(), params);
    }

    /** Busca por nome, e-mail, produto, CPF/CNPJ (só dígitos) ou código da venda. */
    private static void appendSearch(StringBuilder sql, List<Object> params, String query, String charge) {
        if (query == null || query.isBlank()) return;
        String text = query.strip();
        String like = "%" + text.toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        sql.append(" AND (lower(b.name) LIKE ? ESCAPE '\\' OR lower(b.email::text) LIKE ? ESCAPE '\\'"
                + " OR lower(p.name) LIKE ? ESCAPE '\\' OR upper(left(" + charge + ".id::text, 7)) = ?");
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

    private static String placeholders(int count) {
        return String.join(", ", java.util.Collections.nCopies(count, "?"));
    }

    // ---------- mapeamento ----------

    private static String code(UUID id) {
        return id.toString().substring(0, 7).toUpperCase();
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static SaleRow saleRow(ResultSet rs) throws SQLException {
        UUID id = rs.getObject("id", UUID.class);
        return new SaleRow(id, code(id), instant(rs, "created_at"), instant(rs, "confirmed_at"),
                rs.getString("status"), rs.getString("product_name"), rs.getObject("product_id", UUID.class),
                rs.getString("offer_name"), rs.getString("method"), rs.getInt("installments"),
                rs.getString("buyer_name"), rs.getString("buyer_email"), rs.getLong("net_cents"));
    }

    private static SaleDetail detail(ResultSet rs) throws SQLException {
        UUID id = rs.getObject("id", UUID.class);
        String status = rs.getString("status");
        Instant approvedAt = instant(rs, "confirmed_at");
        int cycle = rs.getInt("cycle_number");
        long discount = cycle == 1 ? rs.getLong("discount_cents") : 0;
        long paid = rs.getLong("amount_cents");
        long affiliate = rs.getLong("affiliate_fee_cents");
        long seller = rs.getLong("seller_amount_cents");
        Instant availableAt = approvedAt == null ? null : approvedAt.plusSeconds(rs.getInt("guarantee_days") * 86_400L);
        List<Participant> split = new ArrayList<>();
        split.add(new Participant(rs.getString("seller_name"), "SELLER", seller));
        if (affiliate > 0) split.add(new Participant(rs.getString("affiliate_name"), "AFFILIATE", affiliate));
        UUID subscription = rs.getObject("subscription_id", UUID.class);
        return new SaleDetail(id, code(id), status, "PRODUCER", rs.getString("product_name"),
                rs.getObject("product_id", UUID.class), rs.getString("offer_name"), rs.getString("method"),
                rs.getInt("installments"), instant(rs, "created_at"), approvedAt, availableAt,
                rs.getString("external_ref"), subscription == null ? null : cycle, subscription,
                rs.getString("coupon_code"),
                new Buyer(rs.getString("buyer_name"), rs.getString("buyer_email"), rs.getString("buyer_phone"),
                        rs.getString("tax_id"), rs.getString("person_type"), rs.getString("buyer_ip")),
                new Amounts(Math.addExact(paid, discount), discount, paid, rs.getLong("platform_fee_cents"),
                        affiliate, seller, rs.getLong("refunded_cents"), rs.getLong("net_cents")),
                split, null, "PAID".equals(status) || "PARTIALLY_REFUNDED".equals(status), List.of());
    }

    private static String refundFrom() {
        return """
                  FROM refunds r
                  JOIN charges c ON c.id = r.charge_id
                  JOIN orders o ON o.id = c.order_id
                  JOIN offers f ON f.id = o.offer_id
                  JOIN products p ON p.id = f.product_id
                  JOIN buyers b ON b.id = o.buyer_id
                """;
    }

    private static String refundSelect() {
        return """
                SELECT r.id, r.charge_id, r.amount_cents, r.seller_cents, r.reason, r.status, r.requested_by,
                       r.created_at, r.settled_at, p.name AS product_name, b.name AS buyer_name,
                       b.email::text AS buyer_email, o.buyer_phone
                """ + refundFrom();
    }

    private static RefundRow refundRow(ResultSet rs) throws SQLException {
        UUID chargeId = rs.getObject("charge_id", UUID.class);
        return new RefundRow(rs.getObject("id", UUID.class), chargeId, code(chargeId), rs.getString("product_name"),
                rs.getString("buyer_name"), rs.getString("buyer_email"), rs.getString("buyer_phone"),
                rs.getLong("amount_cents"), rs.getLong("seller_cents"), rs.getString("reason"),
                rs.getString("status"), rs.getString("requested_by"), instant(rs, "created_at"),
                instant(rs, "settled_at"));
    }
}
