package com.paysi.payment.card.adapter;

import com.paysi.payment.card.domain.SaleEvidenceCommand;
import com.paysi.payment.card.port.CardPaymentRepository;
import com.paysi.payment.provider.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcCardPaymentRepository implements CardPaymentRepository {
    private final JdbcTemplate jdbc;

    public JdbcCardPaymentRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<CardChargeContext> lockCharge(UUID chargeId) {
        return jdbc.query("""
                select c.id,o.id,c.amount_cents,o.installments,b.name,b.email::text,b.person_type,b.tax_id,
                       c.seller_amount_cents,c.affiliate_fee_cents,c.platform_fee_cents,
                       c.provider_charge_id,c.provider_status,c.three_ds_result,c.three_ds_challenge_url,
                       c.three_ds_eci,c.pix_fallback_expires_at
                  from charges c join orders o on o.id=c.order_id join buyers b on b.id=o.buyer_id
                 where c.id=? for update of c,o
                """, (rs, row) -> new CardChargeContext(
                rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getLong(3), rs.getInt(4),
                new ProviderBuyer(rs.getString(5), rs.getString(6), rs.getString(7), rs.getString(8)),
                new ProviderSplit(rs.getLong(9), rs.getLong(10), rs.getLong(11)),
                rs.getString(12), status(rs.getString(13)), rs.getString(14), rs.getString(15),
                rs.getString(16), instant(rs.getTimestamp(17))), chargeId).stream().findFirst();
    }

    @Override
    public void saveResult(UUID chargeId, ProviderPaymentResult result,
                           Instant pixAlternativeExpiresAt, Instant now) {
        String chargeStatus = switch (result.status()) {
            case APPROVED -> "PAID";
            case PENDING -> "PENDING";
            case EXPIRED -> "EXPIRED";
            case DECLINED, ERROR -> "FAILED";
        };
        jdbc.update("""
                update charges set status=?,provider_charge_id=?,provider_status=?,provider_fee_cents=?,
                  three_ds_result=?,three_ds_challenge_url=?,three_ds_eci=?,pix_fallback_expires_at=?,
                  paid_at=case when ?='PAID' then ? else paid_at end,
                  confirmed_at=case when ?='PAID' then ? else confirmed_at end
                 where id=?
                """, chargeStatus, result.providerChargeId(), result.status().name(),
                result.providerFeeCents(), result.threeDs().status(), result.threeDs().redirectUrl(),
                result.threeDs().eci(), timestamp(pixAlternativeExpiresAt),
                chargeStatus, Timestamp.from(now), chargeStatus, Timestamp.from(now), chargeId);
        if (chargeStatus.equals("PAID") || chargeStatus.equals("FAILED") || chargeStatus.equals("EXPIRED")) {
            jdbc.update("update orders set status=?,confirmed_at=case when ?='PAID' then ? else confirmed_at end "
                            + "where id=(select order_id from charges where id=?)",
                    chargeStatus.equals("PAID") ? "PAID" : chargeStatus,
                    chargeStatus, Timestamp.from(now), chargeId);
        }
    }

    @Override
    public void saveEvidence(UUID chargeId, SaleEvidenceCommand evidence, ProviderThreeDs threeDs) {
        jdbc.update("""
                insert into sale_evidence(charge_id,ip,user_agent,device_key,terms_hash,terms_accepted_at,
                  three_ds_result,three_ds_eci)
                values (?,cast(? as inet),?,?,?,?,?,?)
                on conflict(charge_id) do update set three_ds_result=excluded.three_ds_result,
                  three_ds_eci=excluded.three_ds_eci
                """, chargeId, evidence.ip(), evidence.userAgent(), evidence.deviceKey(), evidence.termsHash(),
                Timestamp.from(evidence.termsAcceptedAt()), threeDs.status(), threeDs.eci());
    }

    private static ProviderChargeStatus status(String value) {
        return value == null ? null : ProviderChargeStatus.valueOf(value);
    }

    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    private static Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }
}
