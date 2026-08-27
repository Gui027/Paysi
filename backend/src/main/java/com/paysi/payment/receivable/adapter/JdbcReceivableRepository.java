package com.paysi.payment.receivable.adapter;

import com.paysi.payment.receivable.domain.Receivable;
import com.paysi.payment.receivable.domain.ReceivableSchedule.ChargeReceivableTerms;
import com.paysi.payment.receivable.port.ReceivableRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcReceivableRepository implements ReceivableRepository {
    private final JdbcTemplate jdbc;

    public JdbcReceivableRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<ChargeReceivableTerms> lockCharge(UUID chargeId) {
        return jdbc.query("""
                        select c.id,o.installments,c.amount_cents,c.seller_amount_cents,c.affiliate_fee_cents
                          from charges c join orders o on o.id=c.order_id
                         where c.id=? and c.status in ('PAID','PARTIALLY_REFUNDED')
                         for update of c
                        """, (rs, row) -> new ChargeReceivableTerms(rs.getObject(1, UUID.class), rs.getInt(2),
                        rs.getLong(3), rs.getLong(4), rs.getLong(5)), chargeId)
                .stream().findFirst();
    }

    @Override
    public List<Receivable> findByCharge(UUID chargeId) {
        return jdbc.query("""
                        select id,charge_id,installment_number,provider_receivable_id,expected_at,
                               amount_cents,seller_amount_cents,affiliate_amount_cents
                          from receivables where charge_id=? order by installment_number
                        """, (rs, row) -> new Receivable(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                        rs.getInt(3), rs.getString(4), rs.getTimestamp(5).toInstant(), rs.getLong(6),
                        rs.getLong(7), rs.getLong(8)), chargeId);
    }

    @Override
    public void insert(List<Receivable> receivables) {
        receivables.forEach(item -> jdbc.update("""
                        insert into receivables(id,charge_id,installment_number,amount_cents,seller_amount_cents,
                                                affiliate_amount_cents,expected_at,provider_receivable_id)
                        values (?,?,?,?,?,?,?,?)
                        """, item.id(), item.chargeId(), item.sequence(), item.amountCents(),
                item.sellerAmountCents(), item.affiliateAmountCents(), Timestamp.from(item.expectedAt()), item.providerId()));
    }
}
