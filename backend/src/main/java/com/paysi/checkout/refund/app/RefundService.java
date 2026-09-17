package com.paysi.checkout.refund.app;

import com.paysi.checkout.refund.port.RefundRepository;
import com.paysi.checkout.refund.port.RefundRepository.ChargeRefundContext;
import com.paysi.checkout.refund.port.RefundRepository.StoredRefund;
import com.paysi.core.error.ConflictException;
import com.paysi.core.error.NotFoundException;
import com.paysi.core.error.ValidationException;
import com.paysi.ledger.app.LedgerService;
import com.paysi.ledger.domain.*;
import com.paysi.payment.provider.PaymentProvider;
import com.paysi.payment.provider.ProviderRefundRequest;
import com.paysi.payment.provider.ProviderRefundResult;
import com.paysi.payment.split.RefundPart;
import com.paysi.payment.split.RefundSplit;
import com.paysi.payment.split.Split;
import com.paysi.webhook.app.OutboxService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * BE-12.1: um reembolso é um objeto próprio (RF-105), não um acumulador no pedido. A
 * repartição usa o motor RefundSplit (truncagem cumulativa, calculada uma única vez por
 * fatia) e a reversão do vendedor/afiliado cai em cascata GUARANTEE→PENDING→AVAILABLE→DEBT,
 * sem tocar RESERVE (RF-122) — a plataforma absorve sua própria taxa e a do provedor (RF-072).
 */
@Service
public class RefundService {
    private static final UUID SYS_CLEARING = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    private static final UUID SYS_PLATFORM_REVENUE = UUID.fromString("00000000-0000-0000-0000-0000000000c2");
    private static final Set<String> REFUNDABLE_STATUSES = Set.of("PAID", "PARTIALLY_REFUNDED");
    private static final long MINIMUM_PARTIAL_CENTS = 100;

    private final RefundRepository repository;
    private final LedgerService ledger;
    private final PaymentProvider provider;
    private final OutboxService outbox;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public RefundService(RefundRepository repository, LedgerService ledger, PaymentProvider provider,
                          OutboxService outbox) {
        this(repository, ledger, provider, outbox, Clock.systemUTC());
    }

    RefundService(RefundRepository repository, LedgerService ledger, PaymentProvider provider,
                  OutboxService outbox, Clock clock) {
        this.repository = repository;
        this.ledger = ledger;
        this.provider = provider;
        this.outbox = outbox;
        this.clock = clock;
    }

    @Transactional
    public RefundResult refund(UUID sellerId, UUID chargeId, RefundCommand command) {
        requireIdempotencyKey(command.idempotencyKey());

        var replay = repository.findByIdempotencyKey(chargeId, command.idempotencyKey());
        if (replay.isPresent()) {
            var stored = replay.get();
            if (command.amountCents() != null && !command.amountCents().equals(stored.amountCents())) {
                throw new ConflictException("IDEMPOTENCY_KEY_REUSED",
                        "A chave já foi usada com outro valor de reembolso", "Idempotency-Key");
            }
            return fromStored(stored, true);
        }

        ChargeRefundContext charge = repository.lockChargeForRefund(sellerId, chargeId)
                .orElseThrow(() -> new NotFoundException("CHARGE_NOT_FOUND", "Cobrança não encontrada"));
        if (!REFUNDABLE_STATUSES.contains(charge.status())) {
            throw new ValidationException("CHARGE_NOT_REFUNDABLE",
                    "Cobrança não está paga ou já foi totalmente reembolsada", null);
        }

        long remaining = charge.paidCents() - charge.refundedCents();
        long amountCents = command.amountCents() == null ? remaining : command.amountCents();
        if (amountCents <= 0 || amountCents > remaining) {
            throw new ValidationException("REFUND_AMOUNT_INVALID",
                    "Valor do reembolso deve ser positivo e não pode exceder o saldo pago", "amountCents");
        }
        if (amountCents < remaining && amountCents < MINIMUM_PARTIAL_CENTS) {
            throw new ValidationException("REFUND_MINIMUM_PARTIAL",
                    "Reembolso parcial mínimo é de R$ 1,00", "amountCents");
        }

        Split original = reconstructSplit(charge);
        RefundPart part = RefundSplit.slice(original, charge.paidCents(), charge.refundedCents(), amountCents);

        UUID refundId = UUID.nameUUIDFromBytes(
                (chargeId + ":" + command.idempotencyKey()).getBytes(StandardCharsets.UTF_8));
        var providerResult = provider.refund(
                new ProviderRefundRequest(charge.providerChargeId(), amountCents, refundId));
        Instant now = clock.instant();
        String status = providerResult.succeeded() ? "SUCCEEDED" : "FAILED";

        boolean inserted = repository.insertRefund(refundId, chargeId, amountCents, part.sellerCents(),
                part.affiliateCents(), part.platformCents(), part.providerCents(), command.reason(), status,
                providerResult.succeeded() ? providerResult.providerRefundId() : null, command.idempotencyKey(),
                "SELLER", now, providerResult.succeeded() ? now : null);
        if (!inserted) {
            var raced = repository.findByIdempotencyKey(chargeId, command.idempotencyKey())
                    .orElseThrow(() -> new IllegalStateException("Reembolso sumiu após corrida de idempotência"));
            return fromStored(raced, true);
        }
        if (!providerResult.succeeded()) {
            return new RefundResult(refundId, "FAILED", 0, 0, 0, 0, charge.refundedCents(), charge.status(), false);
        }

        reverseLedger(refundId, charge, part);

        long newRefundedCents = charge.refundedCents() + amountCents;
        String newChargeStatus = newRefundedCents >= charge.paidCents() ? "REFUNDED" : "PARTIALLY_REFUNDED";
        repository.applyChargeRefund(chargeId, newRefundedCents, newChargeStatus);
        emitEvent(charge.sellerId(), chargeId, newChargeStatus, newRefundedCents, charge.paidCents() - newRefundedCents);

        return new RefundResult(refundId, status, part.sellerCents(), part.affiliateCents(), part.platformCents(),
                part.providerCents(), newRefundedCents, newChargeStatus, false);
    }

    private void reverseLedger(UUID refundId, ChargeRefundContext charge, RefundPart part) {
        long platformAbsorbed = part.platformCents() + part.providerCents();
        long clearing = part.sellerCents() + platformAbsorbed;
        if (clearing > 0) {
            var counterEntries = new java.util.ArrayList<LedgerEntry>();
            if (platformAbsorbed > 0) {
                counterEntries.add(new LedgerEntry(SYS_PLATFORM_REVENUE, Bucket.SYSTEM, Direction.DEBIT,
                        platformAbsorbed, Origin.OTHER, null));
            }
            counterEntries.add(new LedgerEntry(SYS_CLEARING, Bucket.SYSTEM, Direction.CREDIT, clearing,
                    Origin.OTHER, null));
            var reference = new LedgerReference(ReferenceType.REFUND, refundId + ":seller");
            if (part.sellerCents() > 0) {
                ledger.writeCascadeDebit(TransactionType.REFUND, reference, "Reembolso de venda", charge.sellerId(),
                        part.sellerCents(), Origin.OTHER, counterEntries);
            } else {
                ledger.write(new LedgerCommand(TransactionType.REFUND, reference, "Reembolso de venda",
                        counterEntries));
            }
        }
        if (part.affiliateCents() > 0 && charge.affiliateId() != null) {
            ledger.writeCascadeDebit(TransactionType.REFUND,
                    new LedgerReference(ReferenceType.REFUND, refundId + ":affiliate"), "Reembolso de comissão",
                    charge.affiliateId(), part.affiliateCents(), Origin.OTHER, List.of(
                            new LedgerEntry(SYS_CLEARING, Bucket.SYSTEM, Direction.CREDIT, part.affiliateCents(),
                                    Origin.OTHER, null)));
        }
    }

    private void emitEvent(UUID sellerId, UUID chargeId, String chargeStatus, long refundedCents, long remainingCents) {
        if ("REFUNDED".equals(chargeStatus)) {
            outbox.append(sellerId, "payment.refunded", new RefundedEvent(chargeId, refundedCents));
        } else {
            outbox.append(sellerId, "payment.partially_refunded",
                    new PartiallyRefundedEvent(chargeId, refundedCents, remainingCents));
        }
    }

    private static Split reconstructSplit(ChargeRefundContext charge) {
        long providerCost = charge.providerFeeCents() == null ? 0 : charge.providerFeeCents();
        long platformNet = charge.platformFeeCents() - providerCost;
        return new Split(charge.sellerAmountCents(), charge.affiliateFeeCents(), platformNet, providerCost,
                charge.platformFeeCents());
    }

    private static RefundResult fromStored(StoredRefund stored, boolean replay) {
        return new RefundResult(stored.id(), stored.status(), stored.sellerCents(), stored.affiliateCents(),
                stored.platformCents(), stored.providerCents(), stored.chargeRefundedCentsAfter(),
                stored.chargeStatus(), replay);
    }

    private static void requireIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ValidationException("IDEMPOTENCY_KEY_REQUIRED", "Idempotency-Key é obrigatório",
                    "Idempotency-Key");
        }
    }

    private record RefundedEvent(UUID chargeId, long refundedCents) {
    }

    private record PartiallyRefundedEvent(UUID chargeId, long refundedCents, long remainingCents) {
    }
}
