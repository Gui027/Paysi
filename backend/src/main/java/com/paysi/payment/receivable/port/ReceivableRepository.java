package com.paysi.payment.receivable.port;

import com.paysi.payment.receivable.domain.Receivable;
import com.paysi.payment.receivable.domain.ReceivableSchedule.ChargeReceivableTerms;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReceivableRepository {
    Optional<ChargeReceivableTerms> lockCharge(UUID chargeId);
    List<Receivable> findByCharge(UUID chargeId);
    void insert(List<Receivable> receivables);
}
