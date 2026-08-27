package com.paysi.ledger.port;

import com.paysi.ledger.domain.*;
import java.util.*;
import java.util.function.Supplier;

public interface LedgerRepository {
    <T> T withAccountLocks(Collection<UUID> accountIds, Supplier<T> work);
    Optional<StoredLedgerTransaction> find(TransactionType type, LedgerReference reference);
    long rawBalance(UUID accountId, Bucket bucket);
    Optional<UUID> tryInsertTransaction(LedgerCommand command, String commandHash);
    void insertEntries(UUID transactionId, List<LedgerEntry> entries);
}
