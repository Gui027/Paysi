package com.paysi.ledger.app;

import com.paysi.core.error.ConflictException;
import com.paysi.core.error.ValidationException;
import com.paysi.ledger.domain.*;
import com.paysi.ledger.port.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class LedgerService {
    private final LedgerRepository repository;
    public LedgerService(LedgerRepository repository) { this.repository = repository; }

    @Transactional
    public LedgerWriteResult write(LedgerCommand command) {
        String hash = hash(command);
        Set<UUID> users = command.entries().stream().filter(entry -> entry.bucket().userBucket())
                .map(LedgerEntry::accountId).collect(Collectors.toCollection(TreeSet::new));
        return repository.withAccountLocks(users, () -> writeLocked(command, hash, users));
    }

    private LedgerWriteResult writeLocked(LedgerCommand command, String hash, Set<UUID> users) {
        var existing = repository.find(command.type(), command.reference());
        if (existing.isPresent()) return replay(existing.get(), hash);
        validateBalances(command.entries(), users);
        var inserted = repository.tryInsertTransaction(command, hash);
        if (inserted.isEmpty()) return replay(repository.find(command.type(), command.reference()).orElseThrow(), hash);
        repository.insertEntries(inserted.get(), command.entries());
        return new LedgerWriteResult(inserted.get(), false);
    }

    private void validateBalances(List<LedgerEntry> entries, Set<UUID> users) {
        for (UUID account : users) for (Bucket bucket : Bucket.values()) {
            if (!bucket.userBucket()) continue;
            long delta = entries.stream().filter(e -> e.accountId().equals(account) && e.bucket() == bucket)
                    .mapToLong(e -> e.direction() == Direction.CREDIT ? e.amountCents() : -e.amountCents()).sum();
            if (delta == 0) continue;
            long resulting = Math.addExact(repository.rawBalance(account, bucket), delta);
            if (bucket == Bucket.DEBT && resulting > 0) throw new ValidationException("LEDGER_DEBT_POSITIVE", "Crédito excede a dívida da conta", "entries");
            if (bucket != Bucket.DEBT && resulting < 0) throw new ValidationException("LEDGER_INSUFFICIENT_BALANCE", "Saldo insuficiente no bucket " + bucket, "entries");
        }
    }

    private static LedgerWriteResult replay(StoredLedgerTransaction stored, String hash) {
        if (stored.commandHash() != null && !MessageDigest.isEqual(stored.commandHash().getBytes(StandardCharsets.UTF_8), hash.getBytes(StandardCharsets.UTF_8)))
            throw new ConflictException("LEDGER_NATURAL_KEY_REUSED", "A referência contábil já foi usada com outro conteúdo", "reference");
        return new LedgerWriteResult(stored.id(), true);
    }

    static String hash(LedgerCommand command) {
        String entries = command.entries().stream().sorted(Comparator.comparing((LedgerEntry e) -> e.accountId().toString()).thenComparing(e -> e.bucket().name()).thenComparing(e -> e.direction().name()).thenComparingLong(LedgerEntry::amountCents).thenComparing(e -> e.origin().name()).thenComparing(e -> String.valueOf(e.releaseAt())))
                .map(e -> String.join("|", e.accountId().toString(), e.bucket().name(), e.direction().name(), Long.toString(e.amountCents()), e.origin().name(), String.valueOf(e.releaseAt()))).collect(Collectors.joining(";"));
        String canonical = command.type() + "|" + command.reference().type() + "|" + command.reference().id() + "|" + command.description() + "|" + entries;
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }
}
