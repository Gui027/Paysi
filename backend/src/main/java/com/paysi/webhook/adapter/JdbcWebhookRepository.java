package com.paysi.webhook.adapter;

import com.paysi.webhook.domain.OutboxEvent;
import com.paysi.webhook.domain.WebhookDelivery;
import com.paysi.webhook.domain.WebhookEndpoint;
import com.paysi.webhook.port.WebhookRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.stereotype.Repository;

import java.sql.Array;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public class JdbcWebhookRepository implements WebhookRepository {
    private final JdbcTemplate jdbc;
    public JdbcWebhookRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override public void insertEndpoint(WebhookEndpoint endpoint) {
        jdbc.execute((ConnectionCallback<Integer>) connection -> {
            try (var statement = connection.prepareStatement("insert into webhook_endpoints(id,account_id,url,secret_enc,event_types,disabled_at,created_at) values (?,?,?,?,?,?,?)")) {
                statement.setObject(1, endpoint.id()); statement.setObject(2, endpoint.accountId()); statement.setString(3, endpoint.url()); statement.setBytes(4, endpoint.encryptedSecret());
                Array events = connection.createArrayOf("text", endpoint.events().toArray());
                try { statement.setArray(5, events); statement.setTimestamp(6, endpoint.enabled() ? null : Timestamp.from(endpoint.createdAt())); statement.setTimestamp(7, Timestamp.from(endpoint.createdAt())); return statement.executeUpdate(); }
                finally { events.free(); }
            }
        });
    }

    @Override public List<WebhookEndpoint> listEndpoints(UUID accountId) {
        return jdbc.query("select * from webhook_endpoints where account_id=? and deleted_at is null order by created_at desc", (rs, row) -> endpoint(rs), accountId);
    }

    @Override public Optional<WebhookEndpoint> findEndpoint(UUID accountId, UUID endpointId) {
        return jdbc.query("select * from webhook_endpoints where account_id=? and id=? and deleted_at is null", (rs, row) -> endpoint(rs), accountId, endpointId).stream().findFirst();
    }

    @Override public boolean updateEndpoint(UUID accountId, UUID endpointId, String url, Set<String> events, boolean enabled) {
        Integer changed = jdbc.execute((ConnectionCallback<Integer>) connection -> {
            try (var statement = connection.prepareStatement("update webhook_endpoints set url=?,event_types=?,disabled_at=case when ? then null else coalesce(disabled_at,now()) end where account_id=? and id=? and deleted_at is null")) {
                statement.setString(1, url);
                Array value = connection.createArrayOf("text", events.toArray());
                try { statement.setArray(2, value); statement.setBoolean(3, enabled); statement.setObject(4, accountId); statement.setObject(5, endpointId); return statement.executeUpdate(); }
                finally { value.free(); }
            }
        });
        return changed != null && changed == 1;
    }

    @Override public boolean rotateSecret(UUID accountId, UUID endpointId, byte[] encryptedSecret, Instant now) {
        return jdbc.update("update webhook_endpoints set secret_prev_enc=secret_enc,secret_enc=?,secret_rotated_at=? where account_id=? and id=?", encryptedSecret, Timestamp.from(now), accountId, endpointId) == 1;
    }

    @Override public void insertOutbox(OutboxEvent event) {
        jdbc.update("insert into outbox_events(id,account_id,event_type,payload,created_at) values (?,?,?,cast(? as jsonb),?)", event.id(), event.accountId(), event.type(), event.payload(), Timestamp.from(event.createdAt()));
    }

    @Override public List<OutboxEvent> claimOutbox(Instant now, Instant staleBefore, UUID token, int limit) {
        return jdbc.query("""
                with picked as (select id from outbox_events where published_at is null and (locked_at is null or locked_at<?) order by created_at for update skip locked limit ?)
                update outbox_events e set locked_at=?,lock_token=? from picked p where e.id=p.id
                returning e.id,e.account_id,e.event_type,e.payload::text,e.created_at
                """, (rs, row) -> new OutboxEvent(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getString(3), rs.getString(4), rs.getTimestamp(5).toInstant()), Timestamp.from(staleBefore), limit, Timestamp.from(now), token);
    }

    @Override public void releaseOutbox(UUID eventId, UUID token) { jdbc.update("update outbox_events set locked_at=null,lock_token=null where id=? and lock_token=?", eventId, token); }
    @Override public void markPublished(UUID eventId, UUID token, Instant now) { jdbc.update("update outbox_events set published_at=?,locked_at=null,lock_token=null where id=? and lock_token=?", Timestamp.from(now), eventId, token); }

    @Override public List<WebhookEndpoint> activeEndpoints(UUID accountId, String eventType) {
        return jdbc.query("select * from webhook_endpoints where account_id=? and disabled_at is null and exists (select 1 from unnest(event_types) as subscribed where upper(subscribed)=upper(?))", (rs, row) -> endpoint(rs), accountId, eventType);
    }

    @Override public int nextAttempt(UUID eventId, UUID endpointId) {
        Integer result = jdbc.queryForObject("select coalesce(max(attempt),0)+1 from webhook_deliveries where event_id=? and endpoint_id=?", Integer.class, eventId, endpointId);
        return result == null ? 1 : result;
    }

    @Override public void insertDelivery(WebhookDelivery delivery) {
        jdbc.update("insert into webhook_deliveries(id,event_id,endpoint_id,attempt,status_code,error,next_retry_at,created_at) values (?,?,?,?,?,?,?,?)", delivery.id(), delivery.eventId(), delivery.endpointId(), delivery.attempt(), delivery.httpStatus(), delivery.error(), timestamp(delivery.nextRetryAt()), Timestamp.from(delivery.createdAt()));
    }

    @Override public List<RetryClaim> claimRetries(Instant now, Instant staleBefore, UUID token, int limit) {
        return jdbc.query("""
                with picked as (
                  select d.id from webhook_deliveries d
                   where d.next_retry_at<=? and (d.retry_locked_at is null or d.retry_locked_at<?)
                     and not exists (select 1 from webhook_deliveries newer where newer.event_id=d.event_id and newer.endpoint_id=d.endpoint_id and newer.attempt>d.attempt)
                   order by d.next_retry_at for update skip locked limit ?
                ), claimed as (
                  update webhook_deliveries d set retry_locked_at=?,retry_lock_token=? from picked p where d.id=p.id returning d.*
                )
                select c.id,c.event_id,c.endpoint_id,c.attempt,c.status_code,c.error,c.next_retry_at,c.created_at,
                       e.account_id,e.event_type,e.payload::text,e.created_at,
                       w.id,w.account_id,w.url,w.event_types,w.disabled_at,w.secret_enc,w.secret_prev_enc,w.secret_rotated_at,w.created_at
                  from claimed c join outbox_events e on e.id=c.event_id join webhook_endpoints w on w.id=c.endpoint_id
                """, (rs, row) -> new RetryClaim(
                        new WebhookDelivery(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getObject(3, UUID.class), rs.getInt(4), (Integer) rs.getObject(5), rs.getString(6), instant(rs.getTimestamp(7)), rs.getTimestamp(8).toInstant()),
                        new OutboxEvent(rs.getObject(2, UUID.class), rs.getObject(9, UUID.class), rs.getString(10), rs.getString(11), rs.getTimestamp(12).toInstant()),
                        new WebhookEndpoint(rs.getObject(13, UUID.class), rs.getObject(14, UUID.class), rs.getString(15), strings(rs.getArray(16)), rs.getTimestamp(17) == null, rs.getBytes(18), rs.getBytes(19), instant(rs.getTimestamp(20)), rs.getTimestamp(21).toInstant())),
                Timestamp.from(now), Timestamp.from(staleBefore), limit, Timestamp.from(now), token);
    }

    @Override public void finishRetry(UUID deliveryId, UUID token) { jdbc.update("update webhook_deliveries set next_retry_at=null,retry_locked_at=null,retry_lock_token=null where id=? and retry_lock_token=?", deliveryId, token); }
    @Override public void releaseRetry(UUID deliveryId, UUID token) { jdbc.update("update webhook_deliveries set retry_locked_at=null,retry_lock_token=null where id=? and retry_lock_token=?", deliveryId, token); }

    @Override public List<WebhookDelivery> deliveryHistory(UUID accountId, int limit) {
        return jdbc.query("select d.id,d.event_id,d.endpoint_id,d.attempt,d.status_code,d.error,d.next_retry_at,d.created_at from webhook_deliveries d join webhook_endpoints w on w.id=d.endpoint_id where w.account_id=? order by d.created_at desc limit ?", (rs, row) -> delivery(rs), accountId, Math.min(Math.max(limit, 1), 100));
    }

    @Override public Optional<OutboxEvent> findEvent(UUID accountId, UUID eventId) {
        return jdbc.query("select id,account_id,event_type,payload::text,created_at from outbox_events where account_id=? and id=?", (rs, row) -> new OutboxEvent(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getString(3), rs.getString(4), rs.getTimestamp(5).toInstant()), accountId, eventId).stream().findFirst();
    }

    @Override public void setProfile(UUID accountId, UUID endpointId, String name, UUID productId) {
        jdbc.update("update webhook_endpoints set name=?,product_id=? where account_id=? and id=?", name, productId, accountId, endpointId);
    }

    @Override public boolean softDelete(UUID accountId, UUID endpointId) {
        return jdbc.update("update webhook_endpoints set deleted_at=now(),disabled_at=coalesce(disabled_at,now()) where account_id=? and id=? and deleted_at is null", accountId, endpointId) == 1;
    }

    @Override public void attachDeliveryDetails(UUID deliveryId, String url, String requestBody, String responseBody) {
        jdbc.update("update webhook_deliveries set url=?,request_body=?,response_body=? where id=?", url, requestBody, responseBody, deliveryId);
    }

    @Override public boolean productBelongsTo(UUID accountId, UUID productId) {
        Integer found = jdbc.queryForObject("select count(*) from products where id=? and seller_id=?", Integer.class, productId, accountId);
        return found != null && found > 0;
    }

    @Override public Optional<UUID> productOfCharge(UUID chargeId) {
        return jdbc.query("select f.product_id from charges c join orders o on o.id=c.order_id join offers f on f.id=o.offer_id where c.id=?", (rs, row) -> rs.getObject(1, UUID.class), chargeId).stream().findFirst();
    }

    private static WebhookEndpoint endpoint(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new WebhookEndpoint(rs.getObject("id", UUID.class), rs.getObject("account_id", UUID.class), rs.getString("url"), strings(rs.getArray("event_types")), rs.getTimestamp("disabled_at") == null, rs.getBytes("secret_enc"), rs.getBytes("secret_prev_enc"), instant(rs.getTimestamp("secret_rotated_at")), rs.getTimestamp("created_at").toInstant(), rs.getString("name"), rs.getObject("product_id", UUID.class));
    }
    private static WebhookDelivery delivery(java.sql.ResultSet rs) throws java.sql.SQLException { return new WebhookDelivery(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getObject(3, UUID.class), rs.getInt(4), (Integer) rs.getObject(5), rs.getString(6), instant(rs.getTimestamp(7)), rs.getTimestamp(8).toInstant()); }
    private static Set<String> strings(Array value) throws java.sql.SQLException { return Set.copyOf(Arrays.asList((String[]) value.getArray())); }
    private static Timestamp timestamp(Instant value) { return value == null ? null : Timestamp.from(value); }
    private static Instant instant(Timestamp value) { return value == null ? null : value.toInstant(); }
}
