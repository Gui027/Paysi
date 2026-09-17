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
    /** RF-122: reembolso/estorno cai em cascata, sem tocar a reserva (imposta por contestação, não escolha do vendedor). */
    private static final Bucket[] REFUND_CASCADE = {Bucket.GUARANTEE, Bucket.PENDING, Bucket.AVAILABLE};

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

    /**
     * BE-12.1: debita uma conta em cascata GUARANTEE→PENDING→AVAILABLE, e o que sobrar
     * vira dívida (DEBT), sem tocar RESERVE (RF-122). A alocação por bucket depende do
     * saldo no momento do lock, então a idempotência natural-key aqui não reconfere
     * conteúdo por hash como em write() — a chave natural (type+reference) já nasce de
     * uma operação de negócio idempotente na camada acima (ex.: refundId determinístico).
     */
    @Transactional
    public LedgerWriteResult writeCascadeDebit(TransactionType type, LedgerReference reference, String description,
            UUID debitAccountId, long totalDebitCents, Origin origin, List<LedgerEntry> counterEntries) {
        if (totalDebitCents <= 0) throw new IllegalArgumentException("valor do débito em cascata deve ser positivo");
        Set<UUID> users = new TreeSet<>();
        users.add(debitAccountId);
        for (LedgerEntry entry : counterEntries) if (entry.bucket().userBucket()) users.add(entry.accountId());
        return repository.withAccountLocks(users, () -> {
            var existing = repository.find(type, reference);
            if (existing.isPresent()) return new LedgerWriteResult(existing.get().id(), true);

            List<LedgerEntry> entries = new java.util.ArrayList<>();
            long remaining = totalDebitCents;
            for (Bucket bucket : REFUND_CASCADE) {
                if (remaining <= 0) break;
                long available = Math.max(0, repository.rawBalance(debitAccountId, bucket));
                long take = Math.min(available, remaining);
                if (take > 0) {
                    entries.add(new LedgerEntry(debitAccountId, bucket, Direction.DEBIT, take, origin, null));
                    remaining -= take;
                }
            }
            if (remaining > 0) {
                entries.add(new LedgerEntry(debitAccountId, Bucket.DEBT, Direction.DEBIT, remaining, origin, null));
            }
            entries.addAll(counterEntries);

            var command = new LedgerCommand(type, reference, description, entries);
            validateBalances(entries, users);
            String hash = hash(command);
            var inserted = repository.tryInsertTransaction(command, hash);
            if (inserted.isEmpty()) return new LedgerWriteResult(repository.find(type, reference).orElseThrow().id(), true);
            repository.insertEntries(inserted.get(), entries);
            return new LedgerWriteResult(inserted.get(), false);
        });
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
