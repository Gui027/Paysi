package com.paysi.identity.recovery.app;

import com.paysi.core.error.ValidationException;
import com.paysi.identity.domain.Account;
import com.paysi.identity.domain.AccountStatus;
import com.paysi.identity.domain.InitialMode;
import com.paysi.identity.domain.KycStatus;
import com.paysi.identity.domain.PayoutDelay;
import com.paysi.identity.domain.PersonType;
import com.paysi.identity.domain.TaxId;
import com.paysi.identity.port.AccountRepository;
import com.paysi.identity.port.PasswordHasher;
import com.paysi.identity.recovery.domain.PasswordResetToken;
import com.paysi.identity.recovery.port.PasswordResetTokenStore;
import com.paysi.identity.recovery.port.RecoveryMailSender;
import com.paysi.identity.recovery.port.RecoveryTokenService;
import com.paysi.identity.session.domain.UserSession;
import com.paysi.identity.session.port.SessionStore;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordRecoveryServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-26T12:00:00Z");

    @Test
    void requestHasSameObservableResultAndOnlyEmailsEligibleAccount() {
        var account = activeAccount();
        var store = new MemoryTokenStore();
        var sent = new ArrayList<String>();
        var existing = service(account, store, sent);
        var unknown = service(null, new MemoryTokenStore(), new ArrayList<>());

        existing.request(" USER@EXAMPLE.COM ");
        unknown.request("unknown@example.com");

        assertThat(sent).containsExactly("user@example.com:raw-reset-token");
        assertThat(store.token.expiresAt()).isEqualTo(NOW.plusSeconds(3600));
        assertThat(store.token.tokenHash()).isEqualTo("hash:raw-reset-token");
    }

    @Test
    void resetUsesTokenOnceHashesPasswordAndInvalidatesSessions() {
        var account = activeAccount();
        var store = new MemoryTokenStore();
        store.create(account.id(), "hash:valid", NOW.plusSeconds(3600), NOW.minusSeconds(10));
        var sessions = new RecordingSessions();
        var accounts = new MemoryAccounts(account);
        var service = service(accounts, store, new ArrayList<>(), sessions);

        service.reset("valid", "new-password", "new-password");

        assertThat(accounts.updatedHash).isEqualTo("argon2:new-password");
        assertThat(store.token.usedAt()).isEqualTo(NOW);
        assertThat(sessions.invalidatedAccount).isEqualTo(account.id());
        assertThatThrownBy(() -> service.reset("valid", "new-password", "new-password"))
                .isInstanceOfSatisfying(ValidationException.class,
                        error -> assertThat(error.code()).isEqualTo("PASSWORD_RESET_TOKEN_INVALID"));
    }

    @Test
    void rejectsExpiredTokenAndMismatchedConfirmation() {
        var account = activeAccount();
        var store = new MemoryTokenStore();
        store.create(account.id(), "hash:expired", NOW.minusSeconds(1), NOW.minusSeconds(3700));
        var service = service(account, store, new ArrayList<>());

        assertThatThrownBy(() -> service.reset("expired", "new-password", "new-password"))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> service.reset("expired", "new-password", "different"))
                .isInstanceOfSatisfying(ValidationException.class,
                        error -> assertThat(error.field()).isEqualTo("confirmPassword"));
    }

    private static PasswordRecoveryService service(Account account, MemoryTokenStore store, List<String> sent) {
        return service(new MemoryAccounts(account), store, sent, new RecordingSessions());
    }

    private static PasswordRecoveryService service(MemoryAccounts accounts, MemoryTokenStore store,
                                                   List<String> sent, RecordingSessions sessions) {
        RecoveryTokenService tokens = new RecoveryTokenService() {
            public String generate() { return "raw-reset-token"; }
            public String hash(String raw) { return "hash:" + raw; }
        };
        RecoveryMailSender mail = (email, rawToken) -> sent.add(email + ":" + rawToken);
        PasswordHasher passwords = new PasswordHasher() {
            public String hash(String raw) { return "argon2:" + raw; }
            public boolean matches(String raw, String encoded) { return false; }
        };
        return new PasswordRecoveryService(accounts, store, tokens, mail, passwords, sessions,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static Account activeAccount() {
        return Account.reconstitute(UUID.randomUUID(), "user@example.com", "old-hash", "User", PersonType.PF,
                new TaxId("52998224725"), KycStatus.PENDING, PayoutDelay.D32, 0, AccountStatus.ACTIVE,
                NOW.minusSeconds(3600));
    }

    private static final class MemoryAccounts implements AccountRepository {
        private final Account account;
        private String updatedHash;
        private MemoryAccounts(Account account) { this.account = account; }
        public boolean existsActiveByEmail(String email) { return false; }
        public boolean existsActiveByTaxId(String taxId) { return false; }
        public Optional<Account> findOpenByEmail(String email) { return Optional.ofNullable(account); }
        public Optional<Account> findById(UUID accountId) { return Optional.ofNullable(account); }
        public void updatePassword(UUID accountId, String passwordHash) { updatedHash = passwordHash; }
        public void insert(Account ignored) { }
    }

    private static final class MemoryTokenStore implements PasswordResetTokenStore {
        private PasswordResetToken token;
        public void create(UUID accountId, String hash, Instant expiresAt, Instant createdAt) {
            token = new PasswordResetToken(UUID.randomUUID(), accountId, hash, expiresAt, null, createdAt);
        }
        public Optional<PasswordResetToken> findForUpdate(String hash) {
            return Optional.ofNullable(token).filter(value -> value.tokenHash().equals(hash));
        }
        public void markUsed(UUID tokenId, Instant usedAt) {
            token = new PasswordResetToken(token.id(), token.accountId(), token.tokenHash(), token.expiresAt(),
                    usedAt, token.createdAt());
        }
    }

    private static final class RecordingSessions implements SessionStore {
        private UUID invalidatedAccount;
        public void save(String hash, UserSession session) { }
        public Optional<UserSession> find(String hash) { return Optional.empty(); }
        public void delete(String hash) { }
        public void deleteAllForAccount(UUID accountId) { invalidatedAccount = accountId; }
    }
}
