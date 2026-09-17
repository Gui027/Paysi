package com.paysi.payment.pix.adapter;

import com.paysi.payment.pix.port.PixRepository;
import com.paysi.payment.provider.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcPixRepository implements PixRepository {
    private final JdbcTemplate jdbc;

    public JdbcPixRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<PixChargeContext> lockCharge(UUID chargeId) {
        return jdbc.query("""
                select c.id,o.id,c.amount_cents,b.name,b.email::text,b.person_type,b.tax_id,
                       c.seller_amount_cents,c.affiliate_fee_cents,c.platform_fee_cents,
                       c.provider_charge_id,c.provider_status,c.pix_qr_code,c.payment_expires_at
                  from charges c join orders o on o.id=c.order_id join buyers b on b.id=o.buyer_id
                 where c.id=? and o.method='PIX' for update of c,o
                """, (rs, row) -> new PixChargeContext(
                rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getLong(3),
                new ProviderBuyer(rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7)),
                new ProviderSplit(rs.getLong(8), rs.getLong(9), rs.getLong(10)), rs.getString(11),
                status(rs.getString(12)), rs.getString(13), instant(rs.getTimestamp(14))),
                chargeId).stream().findFirst();
    }

    @Override
    public void saveIssued(UUID chargeId, ProviderPaymentResult result) {
        var data = result.paymentData();
        jdbc.update("""
                update charges set provider_charge_id=?,provider_status=?,provider_fee_cents=?,
                  payment_expires_at=?,pix_qr_code=?,status='PENDING' where id=?
                """, result.providerChargeId(), result.status().name(), result.providerFeeCents(),
                Timestamp.from(data.expiresAt()), data.pixQrCode(), chargeId);
    }

    private static ProviderChargeStatus status(String value) {
        return value == null ? null : ProviderChargeStatus.valueOf(value);
    }

    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }
}
