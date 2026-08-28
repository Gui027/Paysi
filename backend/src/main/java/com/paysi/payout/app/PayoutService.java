package com.paysi.payout.app;

import com.paysi.core.error.ConflictException;
import com.paysi.core.error.ValidationException;
import com.paysi.ledger.app.LedgerService;
import com.paysi.ledger.domain.*;
import com.paysi.payout.port.PayoutProvider;
import com.paysi.payout.port.PayoutRepository;
import com.paysi.security.mfa.app.MfaGuard;
import com.paysi.security.mfa.domain.SensitiveOperation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@Service
public class PayoutService {
    private static final UUID CLEARING_ACCOUNT = UUID.fromString("00000000-0000-0000-0000-0000000000c1");

    private final PayoutRepository repository;
    private final LedgerService ledger;
    private final MfaGuard mfa;
    private final PayoutProvider provider;
    private final long mfaThreshold;

    public PayoutService(PayoutRepository repository, LedgerService ledger, MfaGuard mfa,
                         PayoutProvider provider,
                         @Value("${paysi.payout.mfa-threshold-cents:100000}") long mfaThreshold) {
        this.repository = repository;
        this.ledger = ledger;
        this.mfa = mfa;
        this.provider = provider;
        this.mfaThreshold = mfaThreshold;
    }

    @Transactional
    public PayoutResult request(UUID accountId, long amountCents, UUID bankAccountId,
                                String idempotencyKey, UUID challengeId) {
        validate(amountCents, bankAccountId, idempotencyKey);

        var replay = repository.findPayout(accountId, idempotencyKey);
        if (replay.isPresent()) {
            var payout = replay.get();
            if (payout.amountCents() != amountCents || !payout.bankAccountId().equals(bankAccountId)) {
                throw new ConflictException("IDEMPOTENCY_KEY_REUSED",
                        "A chave já foi usada com outro conteúdo", "Idempotency-Key");
            }
            return new PayoutResult(payout.id(), payout.status(), payout.receiptUrl(), true);
        }

        var bankAccount = repository.lockBank(bankAccountId).orElseThrow(PayoutService::bankUnavailable);
        if (!bankAccount.accountId().equals(accountId) || !bankAccount.available()) {
            throw bankUnavailable();
        }
        if (repository.lockAndReadDebt(accountId) < 0) {
            throw new ConflictException("PAYOUT_BLOCKED_BY_DEBT",
                    "Quite o saldo devedor antes de solicitar saque", null);
        }

        UUID payoutId = UUID.nameUUIDFromBytes(
                (accountId + ":" + idempotencyKey).getBytes(StandardCharsets.UTF_8));
        if (!repository.insertPayout(payoutId, accountId, amountCents, bankAccountId, idempotencyKey)) {
            return request(accountId, amountCents, bankAccountId, idempotencyKey, challengeId);
        }
        if (amountCents >= mfaThreshold) {
            mfa.consume(accountId, challengeId, SensitiveOperation.PAYOUT);
        }

        ledger.write(new LedgerCommand(
                TransactionType.PAYOUT,
                new LedgerReference(ReferenceType.PAYOUT, payoutId.toString()),
                "Solicitação de saque",
                List.of(
                        new LedgerEntry(accountId, Bucket.AVAILABLE, Direction.DEBIT,
                                amountCents, Origin.OTHER, null),
                        new LedgerEntry(CLEARING_ACCOUNT, Bucket.SYSTEM, Direction.CREDIT,
                                amountCents, Origin.OTHER, null))));

        var transfer = provider.requestPix(payoutId, amountCents, bankAccountId);
        repository.markSent(payoutId, transfer.providerTransferId(), transfer.receiptUrl());
        return new PayoutResult(payoutId, "SENT", transfer.receiptUrl(), false);
    }

    private static void validate(long amountCents, UUID bankAccountId, String idempotencyKey) {
        if (amountCents < 200) {
            throw new ValidationException("PAYOUT_MINIMUM",
                    "O saque mínimo é de 200 centavos", "amountCents");
        }
        if (bankAccountId == null) {
            throw new ValidationException("BANK_ACCOUNT_REQUIRED",
                    "Conta bancária é obrigatória", "bankAccountId");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ValidationException("IDEMPOTENCY_KEY_REQUIRED",
                    "Idempotency-Key é obrigatório", "Idempotency-Key");
        }
    }

    private static ValidationException bankUnavailable() {
        return new ValidationException("BANK_ACCOUNT_UNAVAILABLE",
                "Conta bancária não verificada, arquivada ou inexistente", "bankAccountId");
    }
}
