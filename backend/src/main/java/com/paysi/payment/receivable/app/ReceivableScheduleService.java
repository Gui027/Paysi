package com.paysi.payment.receivable.app;

import com.paysi.core.error.ConflictException;
import com.paysi.core.error.ValidationException;
import com.paysi.payment.receivable.domain.ProviderInstallment;
import com.paysi.payment.receivable.domain.Receivable;
import com.paysi.payment.receivable.domain.ReceivableSchedule;
import com.paysi.payment.receivable.port.ReceivableRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ReceivableScheduleService {
    private static final Logger LOG = LoggerFactory.getLogger(ReceivableScheduleService.class);
    private final ReceivableRepository repository;

    public ReceivableScheduleService(ReceivableRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public ReceivableScheduleResult create(UUID chargeId, List<ProviderInstallment> providerSchedule) {
        var charge = repository.lockCharge(chargeId).orElseThrow(() ->
                new ValidationException("CHARGE_NOT_FOUND", "Cobrança não encontrada", "chargeId"));
        final List<Receivable> planned;
        try {
            planned = ReceivableSchedule.create(charge, providerSchedule);
        } catch (IllegalArgumentException | ArithmeticException error) {
            LOG.error("Divergência no cronograma do PSP para chargeId={}: {}", chargeId, error.getMessage());
            throw new ValidationException("RECEIVABLE_SCHEDULE_MISMATCH", error.getMessage(), "installments");
        }

        var existing = repository.findByCharge(chargeId);
        if (!existing.isEmpty()) {
            if (sameSchedule(existing, planned)) return new ReceivableScheduleResult(existing.size(), true);
            LOG.error("Tentativa de alterar cronograma congelado para chargeId={}", chargeId);
            throw new ConflictException("RECEIVABLE_SCHEDULE_CHANGED",
                    "O cronograma desta cobrança já foi persistido com outros valores", "installments");
        }
        repository.insert(planned);
        return new ReceivableScheduleResult(planned.size(), false);
    }

    private static boolean sameSchedule(List<Receivable> existing, List<Receivable> planned) {
        if (existing.size() != planned.size()) return false;
        for (int index = 0; index < existing.size(); index++) {
            var left = existing.get(index);
            var right = planned.get(index);
            if (left.sequence() != right.sequence()
                    || !left.providerId().equals(right.providerId())
                    || !left.expectedAt().equals(right.expectedAt())
                    || left.amountCents() != right.amountCents()
                    || left.sellerAmountCents() != right.sellerAmountCents()
                    || left.affiliateAmountCents() != right.affiliateAmountCents()) return false;
        }
        return true;
    }
}
