package com.paysi.payment.card.app;

import com.paysi.core.error.ConflictException;
import com.paysi.core.error.NotFoundException;
import com.paysi.core.error.ValidationException;
import com.paysi.payment.card.domain.*;
import com.paysi.payment.card.port.CardPaymentRepository;
import com.paysi.payment.provider.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class CardPaymentService {
    private static final Duration PIX_ALTERNATIVE_WINDOW = Duration.ofHours(24);
    private final CardPaymentRepository repository;
    private final PaymentProvider provider;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public CardPaymentService(CardPaymentRepository repository, PaymentProvider provider) {
        this(repository, provider, Clock.systemUTC());
    }

    CardPaymentService(CardPaymentRepository repository, PaymentProvider provider, Clock clock) {
        this.repository = repository;
        this.provider = provider;
        this.clock = clock;
    }

    @Transactional
    public CardPaymentResult start(UUID chargeId, CardPaymentCommand command) {
        var charge = lock(chargeId);
        if (charge.providerStatus() != null) return view(charge, true);
        if (command.installments() != charge.installments()) {
            throw new ValidationException("CARD_INSTALLMENTS_CHANGED",
                    "Parcelamento diverge do pedido", "installments");
        }
        var result = provider.charge(new ProviderPaymentRequest(charge.orderId(), charge.amountCents(),
                ProviderPaymentMethod.CARD, charge.installments(), command.cardToken(),
                charge.buyer(), charge.split()));
        return persist(chargeId, result, command.evidence(), false);
    }

    @Transactional
    public CardPaymentResult confirmThreeDs(UUID chargeId, String challengeToken,
                                             SaleEvidenceCommand evidence) {
        var charge = lock(chargeId);
        if (charge.providerStatus() == ProviderChargeStatus.APPROVED) return view(charge, true);
        if (charge.providerStatus() != ProviderChargeStatus.PENDING
                || charge.providerChargeId() == null || !"CHALLENGE_REQUIRED".equals(charge.threeDsStatus())) {
            throw new ConflictException("THREE_DS_NOT_PENDING", "Cobrança não aguarda desafio 3DS", null);
        }
        var result = provider.confirmThreeDs(new ProviderThreeDsConfirmation(
                charge.orderId(), charge.providerChargeId(), challengeToken));
        return persist(chargeId, result, evidence, false);
    }

    @Transactional
    public Instant requireAvailablePixAlternative(UUID chargeId) {
        Instant expiresAt = lock(chargeId).pixAlternativeExpiresAt();
        if (expiresAt == null || !clock.instant().isBefore(expiresAt)) {
            throw new ConflictException("PIX_ALTERNATIVE_EXPIRED",
                    "A alternativa Pix não está mais disponível", null);
        }
        return expiresAt;
    }

    private CardPaymentResult persist(UUID chargeId, ProviderPaymentResult result,
                                      SaleEvidenceCommand evidence, boolean replay) {
        Instant now = clock.instant();
        Instant pixUntil = result.status() == ProviderChargeStatus.APPROVED
                ? null : now.plus(PIX_ALTERNATIVE_WINDOW);
        repository.saveResult(chargeId, result, pixUntil, now);
        if (result.status() == ProviderChargeStatus.APPROVED) {
            repository.saveEvidence(chargeId, evidence, result.threeDs());
        }
        return view(result, pixUntil, replay);
    }

    private CardPaymentRepository.CardChargeContext lock(UUID chargeId) {
        if (chargeId == null) throw new ValidationException("CHARGE_REQUIRED", "Cobrança é obrigatória", "chargeId");
        return repository.lockCharge(chargeId)
                .orElseThrow(() -> new NotFoundException("CHARGE_NOT_FOUND", "Cobrança não encontrada"));
    }

    private static CardPaymentResult view(CardPaymentRepository.CardChargeContext charge, boolean replay) {
        return new CardPaymentResult(charge.providerChargeId(), publicStatus(charge.providerStatus()),
                new CardPaymentResult.CardThreeDs("CHALLENGE_REQUIRED".equals(charge.threeDsStatus()),
                        charge.threeDsStatus(), charge.challengeUrl(), charge.eci()),
                charge.pixAlternativeExpiresAt(), replay);
    }

    private static CardPaymentResult view(ProviderPaymentResult result, Instant pixUntil, boolean replay) {
        return new CardPaymentResult(result.providerChargeId(), publicStatus(result.status()),
                new CardPaymentResult.CardThreeDs("CHALLENGE_REQUIRED".equals(result.threeDs().status()),
                        result.threeDs().status(), result.threeDs().redirectUrl(), result.threeDs().eci()),
                pixUntil, replay);
    }

    private static String publicStatus(ProviderChargeStatus status) {
        if (status == null) return "pending";
        return switch (status) {
            case APPROVED -> "approved";
            case PENDING -> "pending";
            case DECLINED, EXPIRED, ERROR -> "declined";
        };
    }
}
