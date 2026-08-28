package com.paysi.payment.inbox.adapter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paysi.payment.inbox.domain.ProviderEventPayload;
import com.paysi.payment.inbox.port.ProviderEventRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Repository
public class JdbcProviderEventRepository implements ProviderEventRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public JdbcProviderEventRepository(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public boolean receive(String provider, ProviderEventPayload event,
                           String rawPayload, boolean signatureValid) {
        return jdbc.update("""
                insert into provider_events(provider,provider_event_id,event_type,payload,signature_valid,status,
                  processed_at,error)
                values (?,?,?,cast(? as jsonb),?,case when ? then 'RECEIVED' else 'IGNORED' end,
                  case when ? then null else now() end,case when ? then null else 'INVALID_SIGNATURE' end)
                on conflict(provider,provider_event_id) do nothing
                """, provider, event.providerEventId(), event.eventType(), rawPayload, signatureValid,
                signatureValid, signatureValid, signatureValid) == 1;
    }

    @Override
    public boolean applyEffect(StoredProviderEvent stored) {
        var event = stored.event();
        String chargeStatus = switch (event.eventType()) {
            case "PAYMENT_CONFIRMED" -> "PAID";
            case "PAYMENT_DECLINED" -> "FAILED";
            case "PAYMENT_EXPIRED" -> "EXPIRED";
            default -> null;
        };
        if (chargeStatus == null) return false;
        String providerStatus = switch (chargeStatus) {
            case "PAID" -> "APPROVED";
            case "EXPIRED" -> "EXPIRED";
            default -> "DECLINED";
        };
        int changed = jdbc.update("""
                update charges set status=?,provider_status=?,
                  paid_at=case when ?='PAID' then ? else paid_at end,
                  confirmed_at=case when ?='PAID' then ? else confirmed_at end
                 where id=? and provider_charge_id=?
                """, chargeStatus, providerStatus, chargeStatus, Timestamp.from(event.occurredAt()),
                chargeStatus, Timestamp.from(event.occurredAt()), event.chargeId(), event.providerChargeId());
        if (changed != 1) throw new IllegalStateException("Cobrança do evento não encontrada");
        jdbc.update("update orders set status=?,confirmed_at=case when ?='PAID' then ? else confirmed_at end "
                        + "where id=(select order_id from charges where id=?)",
                chargeStatus, chargeStatus, Timestamp.from(event.occurredAt()), event.chargeId());
        return true;
    }

    @Override
    public void markProcessed(String provider, String eventId, Instant now) {
        jdbc.update("update provider_events set status='PROCESSED',processed_at=?,error=null,next_retry_at=null "
                        + "where provider=? and provider_event_id=?",
                Timestamp.from(now), provider, eventId);
    }

    @Override
    public void markIgnored(String provider, String eventId, Instant now) {
        jdbc.update("update provider_events set status='IGNORED',processed_at=?,error=null,next_retry_at=null "
                        + "where provider=? and provider_event_id=?",
                Timestamp.from(now), provider, eventId);
    }

    @Override
    public void markFailed(String provider, String eventId, String error,
                           int attempt, Instant nextRetryAt) {
        jdbc.update("update provider_events set status='FAILED',error=?,attempt_count=?,next_retry_at=? "
                        + "where provider=? and provider_event_id=?",
                error, attempt, Timestamp.from(nextRetryAt), provider, eventId);
    }

    @Override
    public List<StoredProviderEvent> lockFailed(Instant now, int limit) {
        return jdbc.query("""
                select provider,provider_event_id,event_type,payload::text,attempt_count
                  from provider_events where status='FAILED' and signature_valid
                   and event_type like 'PAYMENT_%'
                   and next_retry_at<=? order by next_retry_at
                 for update skip locked limit ?
                """, (rs, row) -> {
            String raw = rs.getString(4);
            return new StoredProviderEvent(rs.getString(1), parse(raw), raw, rs.getInt(5));
        }, Timestamp.from(now), limit);
    }

    private ProviderEventPayload parse(String raw) {
        try {
            return json.readValue(raw, ProviderEventPayload.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Payload persistido inválido", exception);
        }
    }
}
