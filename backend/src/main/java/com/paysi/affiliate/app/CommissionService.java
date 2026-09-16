package com.paysi.affiliate.app;

import com.paysi.affiliate.port.AffiliateAttributionRepository;
import com.paysi.affiliate.port.AffiliateAttributionRepository.Attribution;
import com.paysi.core.error.NotFoundException;
import com.paysi.core.error.ValidationException;
import com.paysi.ledger.app.LedgerService;
import com.paysi.ledger.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** BE-09.2: registra clique, resolve a atribuição de última origem e liquida a comissão do afiliado. */
@Service
public class CommissionService {
    /** SYS_CLEARING (V029): origem do dinheiro que entra do provedor, debitada em toda venda. */
    private static final UUID SYS_CLEARING = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    private static final Duration CLICK_WINDOW = Duration.ofDays(60);

    private final AffiliateAttributionRepository attribution;
    private final LedgerService ledger;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public CommissionService(AffiliateAttributionRepository attribution, LedgerService ledger) {
        this(attribution, ledger, Clock.systemUTC());
    }

    CommissionService(AffiliateAttributionRepository attribution, LedgerService ledger, Clock clock) {
        this.attribution = attribution;
        this.ledger = ledger;
        this.clock = clock;
    }

    @Transactional
    public UUID registerClick(UUID productId, UUID affiliateId, String visitorKey, String ip) {
        if (visitorKey == null || visitorKey.isBlank()) {
            throw new ValidationException("VISITOR_KEY_REQUIRED", "Identificador do visitante é obrigatório", "visitorKey");
        }
        UUID affiliationId = attribution.findApprovedAffiliation(productId, affiliateId)
                .orElseThrow(() -> new NotFoundException("AFFILIATION_NOT_FOUND",
                        "Não há afiliação aprovada deste afiliado para o produto"));
        Instant now = clock.instant();
        attribution.recordClick(UUID.randomUUID(), affiliationId, productId, visitorKey, ip, now, now.plus(CLICK_WINDOW));
        return affiliationId;
    }

    /** Cliques e pedidos por afiliação aprovada, para a tela "meus links" do afiliado. */
    @Transactional(readOnly = true)
    public List<AffiliateAttributionRepository.LinkStats> myLinks(UUID affiliateId) {
        return attribution.linkStats(affiliateId);
    }

    /**
     * Resolve a atribuição vigente para o comprador atual. {@code cycleNumber > 1} só conta quando
     * a afiliação foi aprovada como recorrente (ALL_CYCLES); do contrário a comissão vale só a
     * primeira cobrança e ciclos seguintes não têm afiliado atribuído.
     */
    @Transactional(readOnly = true)
    public Optional<Attribution> resolveForCharge(UUID productId, String visitorKey, int cycleNumber) {
        if (visitorKey == null || visitorKey.isBlank()) return Optional.empty();
        var found = attribution.resolveAttribution(productId, visitorKey, clock.instant());
        if (found.isEmpty()) return Optional.empty();
        if (cycleNumber > 1 && !found.get().allCycles()) return Optional.empty();
        return found;
    }

    /** Credita a comissão na GUARANTEE do afiliado, liberada nas mesmas regras de garantia da oferta. */
    @Transactional
    public void liquidate(UUID affiliateId, long amountCents, UUID chargeId, Instant confirmedAt, int guaranteeDays) {
        if (amountCents <= 0) return;
        var entries = List.of(
                new LedgerEntry(SYS_CLEARING, Bucket.SYSTEM, Direction.DEBIT, amountCents, Origin.COMMISSION, null),
                new LedgerEntry(affiliateId, Bucket.GUARANTEE, Direction.CREDIT, amountCents, Origin.COMMISSION,
                        confirmedAt.plus(java.time.Duration.ofDays(guaranteeDays))));
        ledger.write(new LedgerCommand(TransactionType.COMMISSION,
                new LedgerReference(ReferenceType.CHARGE, chargeId + ":commission"),
                "Comissão de afiliado", entries));
    }
}
