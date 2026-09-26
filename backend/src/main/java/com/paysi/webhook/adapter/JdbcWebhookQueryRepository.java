package com.paysi.webhook.adapter;

import com.paysi.webhook.app.WebhookPanelModels;
import com.paysi.webhook.app.WebhookPanelModels.EndpointItem;
import com.paysi.webhook.app.WebhookPanelModels.LogDetail;
import com.paysi.webhook.app.WebhookPanelModels.LogFilter;
import com.paysi.webhook.app.WebhookPanelModels.LogRow;
import com.paysi.webhook.port.WebhookQueryRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/** Leituras das telas de Webhooks: lista de endpoints e logs de envio por endpoint. */
@Repository
class JdbcWebhookQueryRepository implements WebhookQueryRepository {
    private static final String ZONE = "America/Sao_Paulo";
    /** Última tentativa de cada evento para o endpoint, com a referência (venda ou assinatura) e o e-mail do comprador. */
    private static final String LATEST = """
            select * from (
              select distinct on (d.event_id)
                     d.event_id, e.event_type, d.created_at, d.attempt, d.status_code, d.error, d.next_retry_at,
                     coalesce(e.payload->>'chargeId', e.payload->>'subscriptionId') as reference,
                     lower(coalesce(e.payload->'buyer'->>'email', '')) as buyer_email
                from webhook_deliveries d
                join outbox_events e on e.id = d.event_id
                join webhook_endpoints w on w.id = d.endpoint_id
               where w.account_id = ? and d.endpoint_id = ?
               order by d.event_id, d.attempt desc
            ) x where 1 = 1
            """;

    private final JdbcTemplate jdbc;

    JdbcWebhookQueryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<EndpointItem> endpoints(UUID accountId, String query, UUID productId) {
        List<Object> params = new ArrayList<>(List.of(accountId));
        StringBuilder sql = new StringBuilder("""
                select w.id, w.name, w.url, w.product_id, p.name as product_name, w.event_types, w.disabled_at, w.created_at
                  from webhook_endpoints w left join products p on p.id = w.product_id
                 where w.account_id = ? and w.deleted_at is null
                """);
        if (productId != null) {
            sql.append(" and w.product_id = ?");
            params.add(productId);
        }
        if (query != null && !query.isBlank()) {
            sql.append(" and (lower(coalesce(w.name, '')) like ? escape '\\' or lower(w.url) like ? escape '\\')");
            String like = "%" + query.strip().toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            params.add(like);
            params.add(like);
        }
        sql.append(" order by w.created_at desc, w.id");
        return jdbc.query(sql.toString(), (rs, row) -> new EndpointItem(rs.getObject("id", UUID.class), rs.getString("name"),
                rs.getString("url"), rs.getObject("product_id", UUID.class), rs.getString("product_name"),
                Set.copyOf(Arrays.asList((String[]) rs.getArray("event_types").getArray())), rs.getTimestamp("disabled_at") == null,
                rs.getTimestamp("created_at").toInstant()), params.toArray());
    }

    private record Where(String sql, List<Object> params) { }

    private static Where where(UUID accountId, UUID endpointId, LogFilter filter) {
        StringBuilder sql = new StringBuilder();
        List<Object> params = new ArrayList<>(List.of(accountId, endpointId));
        if (!filter.eventTypes().isEmpty()) {
            sql.append(" and x.event_type in (").append(String.join(", ", Collections.nCopies(filter.eventTypes().size(), "?"))).append(")");
            params.addAll(new TreeSet<>(filter.eventTypes()));
        }
        if (filter.from() != null) {
            sql.append(" and (x.created_at at time zone '").append(ZONE).append("')::date >= ?");
            params.add(java.sql.Date.valueOf(filter.from()));
        }
        if (filter.to() != null) {
            sql.append(" and (x.created_at at time zone '").append(ZONE).append("')::date <= ?");
            params.add(java.sql.Date.valueOf(filter.to()));
        }
        String query = filter.query();
        if (query != null && !query.isBlank()) {
            String text = query.strip();
            sql.append(" and (x.event_id::text = ? or upper(left(coalesce(x.reference, ''), 7)) = ? or x.buyer_email like ? escape '\\')");
            params.add(text.toLowerCase());
            params.add(text.toUpperCase());
            params.add("%" + text.toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%");
        }
        return new Where(sql.toString(), params);
    }

    @Override
    public List<LogRow> logs(UUID accountId, UUID endpointId, LogFilter filter, int limit, int offset) {
        Where where = where(accountId, endpointId, filter);
        List<Object> params = new ArrayList<>(where.params());
        params.add(limit);
        params.add(offset);
        return jdbc.query(LATEST + where.sql() + " order by x.created_at desc, x.event_id limit ? offset ?", (rs, row) -> {
            Integer status = (Integer) rs.getObject("status_code");
            String reference = rs.getString("reference");
            return new LogRow(rs.getObject("event_id", UUID.class), rs.getString("event_type"),
                    reference == null || reference.length() < 7 ? null : reference.substring(0, 7).toUpperCase(),
                    rs.getTimestamp("created_at").toInstant(),
                    WebhookPanelModels.statusOf(status, rs.getString("error"), instant(rs, "next_retry_at")), status, rs.getInt("attempt"));
        }, params.toArray());
    }

    @Override
    public long countLogs(UUID accountId, UUID endpointId, LogFilter filter) {
        Where where = where(accountId, endpointId, filter);
        Long total = jdbc.queryForObject("select count(*) from (" + LATEST + where.sql() + ") counted", Long.class, where.params().toArray());
        return total == null ? 0 : total;
    }

    @Override
    public Optional<LogDetail> logDetail(UUID accountId, UUID endpointId, UUID eventId) {
        return jdbc.query("""
                select d.attempt, d.status_code, d.error, d.next_retry_at, d.created_at,
                       coalesce(d.url, w.url) as url, d.request_body, d.response_body, e.event_type, w.disabled_at
                  from webhook_deliveries d
                  join webhook_endpoints w on w.id = d.endpoint_id
                  join outbox_events e on e.id = d.event_id
                 where w.account_id = ? and d.endpoint_id = ? and d.event_id = ?
                 order by d.attempt desc limit 1
                """, (rs, row) -> {
            Integer status = (Integer) rs.getObject("status_code");
            return new LogDetail(eventId, rs.getString("event_type"), rs.getString("url"), rs.getTimestamp("created_at").toInstant(),
                    WebhookPanelModels.statusOf(status, rs.getString("error"), instant(rs, "next_retry_at")), status,
                    rs.getString("error"), rs.getString("request_body"), rs.getString("response_body"), rs.getInt("attempt"),
                    rs.getTimestamp("disabled_at") == null);
        }, accountId, endpointId, eventId).stream().findFirst();
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
}
