package com.paysi.payment.boleto.app;

import com.paysi.core.error.NotFoundException;
import com.paysi.core.error.ValidationException;
import com.paysi.payment.boleto.domain.BoletoResult;
import com.paysi.payment.boleto.port.BoletoRepository;
import com.paysi.payment.provider.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

@Service
public class BoletoPaymentService {
    private final BoletoRepository repository;
    private final PaymentProvider provider;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public BoletoPaymentService(BoletoRepository repository, PaymentProvider provider) {
        this(repository, provider, Clock.systemUTC());
    }

    BoletoPaymentService(BoletoRepository repository, PaymentProvider provider, Clock clock) {
        this.repository = repository;
        this.provider = provider;
        this.clock = clock;
    }

    @Transactional
    public BoletoResult issue(UUID chargeId, int dueDays) {
        if (dueDays < 1 || dueDays > 15) {
            throw new ValidationException("BOLETO_DUE_DAYS_INVALID",
                    "Vencimento precisa estar entre 1 e 15 dias", "dueDays");
        }
        var charge = repository.lockCharge(chargeId)
                .orElseThrow(() -> new NotFoundException("BOLETO_CHARGE_NOT_FOUND", "Cobrança não encontrada"));
        if (charge.providerStatus() != null) return view(charge, true);
        var result = provider.charge(new ProviderPaymentRequest(charge.orderId(), charge.amountCents(),
                ProviderPaymentMethod.BOLETO, 1, null, charge.buyer(), charge.split(), dueDays));
        if (result.paymentData() == null || result.paymentData().boletoBarcode() == null
                || result.paymentData().boletoUrl() == null || result.paymentData().expiresAt() == null) {
            throw new IllegalStateException("Provedor não retornou os dados do boleto");
        }
        repository.saveIssued(chargeId, result);
        return new BoletoResult(result.providerChargeId(), result.paymentData().boletoBarcode(),
                result.paymentData().boletoUrl(), result.paymentData().expiresAt(),
                result.status().name(), false);
    }

    @Transactional
    public int expireDue() {
        return repository.expireDue(clock.instant());
    }

    private static BoletoResult view(BoletoRepository.BoletoChargeContext charge, boolean replay) {
        return new BoletoResult(charge.providerChargeId(), charge.barcode(), charge.pdfUrl(), charge.dueAt(),
                charge.providerStatus().name(), replay);
    }
}
