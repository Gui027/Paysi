package com.paysi.payout.web;

import com.paysi.identity.session.app.SessionService;
import com.paysi.payout.app.BankAccountService;
import com.paysi.payout.app.PayoutResult;
import com.paysi.payout.app.PayoutService;
import com.paysi.payout.domain.BankAccount;
import com.paysi.payout.domain.BankAccountCommand;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/v1/accounts/me")
public class PayoutController {
    private static final String SESSION_COOKIE = "paysi_session";

    private final BankAccountService bankAccounts;
    private final PayoutService payouts;
    private final SessionService sessions;

    public PayoutController(BankAccountService bankAccounts, PayoutService payouts, SessionService sessions) {
        this.bankAccounts = bankAccounts;
        this.payouts = payouts;
        this.sessions = sessions;
    }

    @PostMapping("/bank-accounts")
    public ResponseEntity<BankAccountView> createBankAccount(
            @CookieValue(name = SESSION_COOKIE, required = false) String token,
            @RequestHeader("X-MFA-Challenge-Id") UUID challengeId,
            @RequestBody BankAccountCommand command) {
        var bankAccount = bankAccounts.create(accountId(token), command, challengeId);
        return ResponseEntity.status(HttpStatus.CREATED).body(BankAccountView.from(bankAccount));
    }

    @DeleteMapping("/bank-accounts/{bankAccountId}")
    public ResponseEntity<Void> archiveBankAccount(
            @CookieValue(name = SESSION_COOKIE, required = false) String token,
            @RequestHeader("X-MFA-Challenge-Id") UUID challengeId,
            @PathVariable UUID bankAccountId) {
        bankAccounts.archive(accountId(token), bankAccountId, challengeId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/payouts")
    public ResponseEntity<PayoutResult> requestPayout(
            @CookieValue(name = SESSION_COOKIE, required = false) String token,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody PayoutRequest request) {
        var payout = payouts.request(accountId(token), request.amountCents(), request.bankAccountId(),
                idempotencyKey, request.mfaChallengeId());
        return ResponseEntity.status(HttpStatus.CREATED).body(payout);
    }

    private UUID accountId(String token) {
        return sessions.authenticate(token).session().accountId();
    }

    public record PayoutRequest(long amountCents, UUID bankAccountId, UUID mfaChallengeId) {}

    public record BankAccountView(UUID id, String bankCode, String branch,
                                  String numberLast4, Instant verifiedAt) {
        static BankAccountView from(BankAccount bankAccount) {
            return new BankAccountView(bankAccount.id(), bankAccount.bankCode(), bankAccount.branch(),
                    bankAccount.numberLast4(), bankAccount.verifiedAt());
        }
    }
}
