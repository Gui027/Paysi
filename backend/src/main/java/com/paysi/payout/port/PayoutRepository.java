package com.paysi.payout.port;

import com.paysi.payout.domain.BankAccount;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface PayoutRepository {
    Optional<AccountHolder> accountHolder(UUID accountId);
    void insertBank(BankAccount bank, byte[] number, byte[] pixKey);
    Optional<BankAccount> lockBank(UUID bankId);
    void archiveBank(UUID accountId, UUID bankId, Instant now);
    Optional<PayoutState> findPayout(UUID accountId, String idempotencyKey);
    long lockAndReadDebt(UUID accountId);
    boolean insertPayout(UUID payoutId, UUID accountId, long amountCents, UUID bankId, String idempotencyKey);
    void markSent(UUID payoutId, String providerTransferId, String receiptUrl);
    record AccountHolder(String taxId, String fullName) { }
    record PayoutState(UUID id, long amountCents, UUID bankAccountId, String status, String receiptUrl) { }
}
