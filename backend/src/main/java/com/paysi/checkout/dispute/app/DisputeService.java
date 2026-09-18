package com.paysi.checkout.dispute.app;

import com.paysi.checkout.dispute.port.DisputeRepository;
import com.paysi.checkout.dispute.port.DisputeRepository.BucketAmount;
import com.paysi.checkout.dispute.port.DisputeRepository.ChargeDisputeContext;
import com.paysi.checkout.dispute.port.DisputeRepository.StoredDispute;
import com.paysi.core.error.ConflictException;
import com.paysi.core.error.NotFoundException;
import com.paysi.core.error.ValidationException;
import com.paysi.ledger.app.LedgerService;
import com.paysi.ledger.domain.*;
import com.paysi.payment.split.RefundPart;
import com.paysi.payment.split.RefundSplit;
import com.paysi.payment.split.Split;
import com.paysi.risk.app.RiskService;
import com.paysi.webhook.app.OutboxService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * BE-12.2: contestação (chargeback) é risco imposto pelo comprador, não devolução escolhida pelo
 * vendedor (RF-073) — por isso debita a reserva primeiro, ao contrário do reembolso (RF-122). A
 * abertura é idempotente pela notificação do adquirente ({@code provider_dispute_id}, único em
 * V015); o resultado (WON/LOST) reverte exatamente o que foi debitado, lendo de volta os próprios
 * lançamentos imutáveis do razão em vez de recalcular — a mesma garantia de reprodutibilidade que
 * o pacote de defesa (RF-075) exige.
 */
@Service
public class DisputeService {
    private static final UUID SYS_CLEARING = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    private static final UUID SYS_ACQUIRER_FEE = UUID.fromString("00000000-0000-0000-0000-0000000000c6");
    private static final UUID SYS_CHARGEBACK_LOSS = UUID.fromString("00000000-0000-0000-0000-0000000000c5");
    private static final Set<String> DISPUTABLE_STATUSES = Set.of("PAID", "PARTIALLY_REFUNDED");
    private static final Set<String> RESOLVABLE_STATUSES = Set.of("OPEN", "DEFENDED");
    /** Janela padrão de resposta quando o adquirente não informa prazo — ajustável por chamada. */
    private static final long DEFAULT_RESPONSE_WINDOW_DAYS = 7;

    private final DisputeRepository repository;
    private final LedgerService ledger;
    private final RiskService risk;
    private final OutboxService outbox;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public DisputeService(DisputeRepository repository, LedgerService ledger, RiskService risk,
                           OutboxService outbox) {
        this(repository, ledger, risk, outbox, Clock.systemUTC());
    }

    DisputeService(DisputeRepository repository, LedgerService ledger, RiskService risk, OutboxService outbox,
                    Clock clock) {
        this.repository = repository;
        this.ledger = ledger;
        this.risk = risk;
        this.outbox = outbox;
        this.clock = clock;
    }

    @Transactional
    public DisputeResult open(UUID sellerId, UUID chargeId, DisputeCommand command) {
        requireProviderDisputeId(command.providerDisputeId());

        var replay = repository.findByProviderDisputeId(command.providerDisputeId());
        if (replay.isPresent()) {
            var stored = replay.get();
            if (!stored.chargeId().equals(chargeId)) {
                throw new ConflictException("PROVIDER_DISPUTE_ID_REUSED",
                        "O identificador da contestação já foi usado para outra cobrança", "providerDisputeId");
            }
            if (command.amountCents() != null && !command.amountCents().equals(stored.amountCents())) {
                throw new ConflictException("PROVIDER_DISPUTE_ID_REUSED",
                        "O identificador da contestação já foi usado com outro valor", "providerDisputeId");
            }
            return fromStored(stored, true);
        }

        ChargeDisputeContext charge = repository.lockChargeForDispute(sellerId, chargeId)
                .orElseThrow(() -> new NotFoundException("CHARGE_NOT_FOUND", "Cobrança não encontrada"));
        if (!DISPUTABLE_STATUSES.contains(charge.status())) {
            throw new ValidationException("CHARGE_NOT_DISPUTABLE",
                    "Cobrança não está paga ou já foi totalmente reembolsada", null);
        }

        long remaining = charge.paidCents() - charge.refundedCents();
        long amountCents = command.amountCents() == null ? remaining : command.amountCents();
        if (amountCents <= 0 || amountCents > remaining) {
            throw new ValidationException("DISPUTE_AMOUNT_INVALID",
                    "Valor da contestação deve ser positivo e não pode exceder o saldo pago", "amountCents");
        }
        long acquirerFeeCents = command.acquirerFeeCents() == null ? 0 : command.acquirerFeeCents();
        if (acquirerFeeCents < 0) {
            throw new ValidationException("DISPUTE_FEE_INVALID", "Tarifa da adquirente não pode ser negativa",
                    "acquirerFeeCents");
        }

        Instant now = clock.instant();
        Instant deadlineAt = command.deadlineAt() != null ? command.deadlineAt()
                : now.plus(DEFAULT_RESPONSE_WINDOW_DAYS, ChronoUnit.DAYS);

        UUID disputeId = UUID.nameUUIDFromBytes(
                ("dispute:" + command.providerDisputeId()).getBytes(StandardCharsets.UTF_8));

        boolean inserted = repository.insertDispute(disputeId, chargeId, amountCents, acquirerFeeCents,
                command.reason(), "OPEN", deadlineAt, command.providerDisputeId(), now);
        if (!inserted) {
            var raced = repository.findByProviderDisputeId(command.providerDisputeId())
                    .orElseThrow(() -> new IllegalStateException("Disputa sumiu após corrida de idempotência"));
            return fromStored(raced, true);
        }

        var allocated = reverseLedgerOnOpen(disputeId, charge, amountCents, acquirerFeeCents);
        repository.applyChargeDisputeStatus(chargeId, "CHARGEBACK");
        outbox.append(charge.sellerId(), "chargeback.opened",
                new ChargebackOpenedEvent(chargeId, disputeId, amountCents, acquirerFeeCents, deadlineAt));
        risk.recalculateSeller(charge.sellerId());

        return new DisputeResult(disputeId, chargeId, "OPEN", allocated.sellerCents(), allocated.affiliateCents(),
                deadlineAt, false);
    }

    @Transactional
    public DisputeResult resolve(UUID sellerId, UUID disputeId, DisputeOutcome outcome, String memo) {
        StoredDispute stored = repository.lockDisputeForResolution(sellerId, disputeId)
                .orElseThrow(() -> new NotFoundException("DISPUTE_NOT_FOUND", "Disputa não encontrada"));

        if (stored.status().equals(outcome.name())) {
            return fromStored(stored, true);
        }
        if (!RESOLVABLE_STATUSES.contains(stored.status())) {
            throw new ConflictException("DISPUTE_ALREADY_RESOLVED",
                    "Disputa já foi resolvida com um resultado diferente", "status");
        }

        if (outcome == DisputeOutcome.WON) {
            reverseLedgerOnWon(stored);
            repository.applyChargeDisputeStatus(stored.chargeId(), "PAID");
        }
        // LOST: o débito original permanece como perda final — nenhum novo lançamento.

        repository.updateDisputeStatus(disputeId, outcome.name());
        risk.recalculateSeller(stored.sellerId());

        return new DisputeResult(disputeId, stored.chargeId(), outcome.name(), stored.amountCents(), 0,
                stored.deadlineAt(), false);
    }

    public EvidencePackage evidencePackage(UUID sellerId, UUID disputeId) {
        StoredDispute dispute = repository.findDisputeForSeller(sellerId, disputeId)
                .orElseThrow(() -> new NotFoundException("DISPUTE_NOT_FOUND", "Disputa não encontrada"));
        var evidence = repository.findEvidence(dispute.chargeId());
        if (evidence.isEmpty()) {
            return new EvidencePackage(dispute.id(), dispute.chargeId(), dispute.status(), dispute.deadlineAt(),
                    dispute.amountCents(), dispute.acquirerFeeCents(), dispute.reason(), null, null, null, null,
                    null, null, null, null, null);
        }
        var snapshot = evidence.get();
        return new EvidencePackage(dispute.id(), dispute.chargeId(), dispute.status(), dispute.deadlineAt(),
                dispute.amountCents(), dispute.acquirerFeeCents(), dispute.reason(), snapshot.ip(),
                snapshot.userAgent(), snapshot.deviceKey(), snapshot.termsHash(), snapshot.termsAcceptedAt(),
                snapshot.threeDsResult(), snapshot.emailDeliveredAt(), snapshot.emailOpenedAt(),
                snapshot.accessLogJson());
    }

    /** RF-074: fecha as lacunas de evidência que a venda não captura sozinha (entrega/abertura de e-mail e acesso). */
    public void recordEmailDelivered(UUID chargeId, Instant deliveredAt) {
        repository.recordEmailDelivered(chargeId, deliveredAt);
    }

    public void recordEmailOpened(UUID chargeId, Instant openedAt) {
        repository.recordEmailOpened(chargeId, openedAt);
    }

    public void recordAccess(UUID chargeId, String eventJson) {
        repository.appendAccessLog(chargeId, eventJson);
    }

    private AllocatedParts reverseLedgerOnOpen(UUID disputeId, ChargeDisputeContext charge, long amountCents,
                                                long acquirerFeeCents) {
        Split original = reconstructSplit(charge);
        RefundPart part = RefundSplit.slice(original, charge.paidCents(), charge.refundedCents(), amountCents);
        long affiliateCents = part.affiliateCents();
        // RF-073: a contestação é debitada do vendedor — o que normalmente a plataforma absorve
        // no reembolso (RF-072) aqui vira exposição do vendedor, mais a tarifa da adquirente.
        long sellerCents = amountCents + acquirerFeeCents - affiliateCents;

        if (sellerCents > 0) {
            var counterEntries = new ArrayList<LedgerEntry>();
            long clearingShare = amountCents - affiliateCents;
            if (clearingShare > 0) {
                counterEntries.add(new LedgerEntry(SYS_CLEARING, Bucket.SYSTEM, Direction.CREDIT, clearingShare,
                        Origin.OTHER, null));
            }
            if (acquirerFeeCents > 0) {
                counterEntries.add(new LedgerEntry(SYS_ACQUIRER_FEE, Bucket.SYSTEM, Direction.CREDIT,
                        acquirerFeeCents, Origin.OTHER, null));
            }
            var reference = new LedgerReference(ReferenceType.DISPUTE, disputeId + ":seller");
            ledger.writeDisputeCascadeDebit(TransactionType.CHARGEBACK, reference, "Contestação de cobrança",
                    charge.sellerId(), sellerCents, Origin.OTHER, counterEntries);
        }
        if (affiliateCents > 0 && charge.affiliateId() != null) {
            ledger.writeCascadeDebit(TransactionType.CHARGEBACK,
                    new LedgerReference(ReferenceType.DISPUTE, disputeId + ":affiliate"),
                    "Estorno de comissão por contestação", charge.affiliateId(), affiliateCents, Origin.OTHER,
                    List.of(new LedgerEntry(SYS_CLEARING, Bucket.SYSTEM, Direction.CREDIT, affiliateCents,
                            Origin.OTHER, null)));
        }
        return new AllocatedParts(sellerCents, affiliateCents);
    }

    private void reverseLedgerOnWon(StoredDispute dispute) {
        List<BucketAmount> sellerAllocation = repository.sellerOpeningAllocation(dispute.id());
        List<BucketAmount> affiliateAllocation = repository.affiliateOpeningAllocation(dispute.id());

        List<LedgerEntry> entries = new ArrayList<>();
        for (BucketAmount allocation : sellerAllocation) {
            entries.add(new LedgerEntry(dispute.sellerId(), Bucket.valueOf(allocation.bucket()), Direction.CREDIT,
                    allocation.amountCents(), Origin.OTHER, null));
        }
        if (dispute.affiliateId() != null) {
            for (BucketAmount allocation : affiliateAllocation) {
                entries.add(new LedgerEntry(dispute.affiliateId(), Bucket.valueOf(allocation.bucket()),
                        Direction.CREDIT, allocation.amountCents(), Origin.OTHER, null));
            }
        }
        // Restitui na ordem inversa da cascata (DEBT quita primeiro): o provedor devolve o valor
        // contestado, mas a tarifa da adquirente costuma NÃO voltar — a plataforma absorve (PEN-23).
        entries.add(new LedgerEntry(SYS_CLEARING, Bucket.SYSTEM, Direction.DEBIT, dispute.amountCents(),
                Origin.OTHER, null));
        if (dispute.acquirerFeeCents() > 0) {
            entries.add(new LedgerEntry(SYS_CHARGEBACK_LOSS, Bucket.SYSTEM, Direction.DEBIT,
                    dispute.acquirerFeeCents(), Origin.OTHER, null));
        }

        var reference = new LedgerReference(ReferenceType.DISPUTE, dispute.id() + ":reversal");
        ledger.write(new LedgerCommand(TransactionType.CHARGEBACK_REVERSAL, reference,
                "Contestação ganha na defesa - reversão", entries));
    }

    private static Split reconstructSplit(ChargeDisputeContext charge) {
        long providerCost = charge.providerFeeCents() == null ? 0 : charge.providerFeeCents();
        long platformNet = charge.platformFeeCents() - providerCost;
        return new Split(charge.sellerAmountCents(), charge.affiliateFeeCents(), platformNet, providerCost,
                charge.platformFeeCents());
    }

    private static DisputeResult fromStored(StoredDispute stored, boolean replay) {
        return new DisputeResult(stored.id(), stored.chargeId(), stored.status(), stored.amountCents(), 0,
                stored.deadlineAt(), replay);
    }

    private static void requireProviderDisputeId(String providerDisputeId) {
        if (providerDisputeId == null || providerDisputeId.isBlank()) {
            throw new ValidationException("PROVIDER_DISPUTE_ID_REQUIRED",
                    "Identificador da contestação no adquirente é obrigatório", "providerDisputeId");
        }
    }

    private record AllocatedParts(long sellerCents, long affiliateCents) {
    }

    private record ChargebackOpenedEvent(UUID chargeId, UUID disputeId, long amountCents, long acquirerFeeCents,
                                          Instant deadlineAt) {
    }
}
