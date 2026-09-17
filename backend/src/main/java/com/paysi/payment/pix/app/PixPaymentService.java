package com.paysi.payment.pix.app;

import com.paysi.core.error.NotFoundException;
import com.paysi.payment.pix.domain.PixResult;
import com.paysi.payment.pix.port.PixRepository;
import com.paysi.payment.provider.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * BE-06.2: gera o QR/copia-e-cola do Pix. Como o pagador ainda precisa efetivamente
 * pagar, a cobrança fica PENDING até a confirmação chegar pelo inbox do provedor —
 * mesma convenção do boleto (BE-06.4).
 */
@Service
public class PixPaymentService {
    private final PixRepository repository;
    private final PaymentProvider provider;

    public PixPaymentService(PixRepository repository, PaymentProvider provider) {
        this.repository = repository;
        this.provider = provider;
    }

    @Transactional
    public PixResult start(UUID chargeId) {
        var charge = repository.lockCharge(chargeId)
                .orElseThrow(() -> new NotFoundException("PIX_CHARGE_NOT_FOUND", "Cobrança não encontrada"));
        if (charge.providerStatus() != null) return view(charge, true);

        var result = provider.charge(new ProviderPaymentRequest(charge.orderId(), charge.amountCents(),
                ProviderPaymentMethod.PIX, 1, null, charge.buyer(), charge.split()));
        if (result.paymentData() == null || result.paymentData().pixQrCode() == null
                || result.paymentData().expiresAt() == null) {
            throw new IllegalStateException("Provedor não retornou os dados do Pix");
        }
        repository.saveIssued(chargeId, result);
        return new PixResult(result.providerChargeId(), result.paymentData().pixQrCode(),
                result.paymentData().expiresAt(), "PENDING", false);
    }

    private static PixResult view(PixRepository.PixChargeContext charge, boolean replay) {
        return new PixResult(charge.providerChargeId(), charge.qrCode(), charge.expiresAt(),
                charge.providerStatus() == null ? "PENDING" : charge.providerStatus().name(), replay);
    }
}
