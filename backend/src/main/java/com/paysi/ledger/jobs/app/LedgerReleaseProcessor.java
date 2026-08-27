package com.paysi.ledger.jobs.app;

import com.paysi.ledger.app.LedgerService;
import com.paysi.ledger.domain.*;
import com.paysi.ledger.jobs.domain.DueRelease;
import com.paysi.ledger.jobs.port.LedgerReleaseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Service
public class LedgerReleaseProcessor {
    private final LedgerReleaseRepository releases;
    private final LedgerService ledger;
    private final Clock clock;

    public LedgerReleaseProcessor(LedgerReleaseRepository releases, LedgerService ledger) {
        this(releases, ledger, Clock.systemUTC());
    }

    LedgerReleaseProcessor(LedgerReleaseRepository releases, LedgerService ledger, Clock clock) {
        this.releases = releases;
        this.ledger = ledger;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean processNext(Bucket bucket) {
        var due = releases.claimNext(bucket, clock.instant());
        if (due.isEmpty()) return false;
        long debt = due.get().bucket() == Bucket.GUARANTEE
                ? releases.lockAndReadDebt(due.get().accountId()) : 0;
        var command = command(due.get(), debt);
        var result = ledger.write(command);
        if (!releases.markReleased(due.get().entryId(), result.transactionId(), clock.instant())) {
            throw new IllegalStateException("A liberação deixou de estar disponível durante o processamento");
        }
        return true;
    }

    LedgerCommand command(DueRelease due, long debtBalanceCents) {
        var entries = switch (due.bucket()) {
            case GUARANTEE -> releaseGuarantee(due, debtBalanceCents);
            case PENDING -> releasePending(due);
            case RESERVE -> move(due, Bucket.RESERVE, Bucket.AVAILABLE);
            default -> throw new IllegalArgumentException("Bucket não liberável: " + due.bucket());
        };
        var type = switch (due.bucket()) {
            case GUARANTEE -> TransactionType.GUARANTEE_RELEASE;
            case PENDING -> TransactionType.RELEASE;
            case RESERVE -> TransactionType.RESERVE_RELEASE;
            default -> throw new IllegalArgumentException("Bucket não liberável: " + due.bucket());
        };
        return new LedgerCommand(type,
                new LedgerReference(ReferenceType.RECEIVABLE, "release-entry-" + due.entryId()),
                "Liberação agendada de " + due.bucket(), entries);
    }

    private static List<LedgerEntry> releaseGuarantee(DueRelease due, long debtBalanceCents) {
        long debt = Math.min(debtBalanceCents, due.amountCents());
        long pending = due.amountCents() - debt;
        var entries = new ArrayList<LedgerEntry>();
        entries.add(entry(due, Bucket.GUARANTEE, Direction.DEBIT, due.amountCents(), null));
        if (debt > 0) entries.add(new LedgerEntry(due.accountId(), Bucket.DEBT, Direction.CREDIT,
                debt, Origin.DEBT, null));
        if (pending > 0) entries.add(entry(due, Bucket.PENDING, Direction.CREDIT, pending, due.pendingReleaseAt()));
        return List.copyOf(entries);
    }

    private static List<LedgerEntry> releasePending(DueRelease due) {
        int reserveBps = due.origin() == Origin.COMMISSION ? 0 : reserveBps(due.payoutDelay());
        long reserve = Math.multiplyExact(due.amountCents(), reserveBps) / 10_000;
        long available = due.amountCents() - reserve;
        var entries = new ArrayList<LedgerEntry>();
        entries.add(entry(due, Bucket.PENDING, Direction.DEBIT, due.amountCents(), null));
        if (available > 0) entries.add(entry(due, Bucket.AVAILABLE, Direction.CREDIT, available, null));
        if (reserve > 0) entries.add(entry(due, Bucket.RESERVE, Direction.CREDIT, reserve,
                due.sourceCreatedAt().plus(90, ChronoUnit.DAYS)));
        return List.copyOf(entries);
    }

    private static List<LedgerEntry> move(DueRelease due, Bucket from, Bucket to) {
        return List.of(entry(due, from, Direction.DEBIT, due.amountCents(), null),
                entry(due, to, Direction.CREDIT, due.amountCents(), null));
    }

    private static LedgerEntry entry(DueRelease due, Bucket bucket, Direction direction, long amount,
                                     java.time.Instant releaseAt) {
        return new LedgerEntry(due.accountId(), bucket, direction, amount, due.origin(), releaseAt);
    }

    private static int reserveBps(String payoutDelay) {
        return switch (payoutDelay) {
            case "D32" -> 400;
            case "D15" -> 600;
            case "D7" -> 800;
            case "D2" -> 1_000;
            default -> throw new IllegalArgumentException("Prazo de recebimento inválido: " + payoutDelay);
        };
    }
}
