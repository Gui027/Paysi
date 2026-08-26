package com.paysi.identity.session.app;

import com.paysi.core.error.ForbiddenException;
import com.paysi.core.error.UnauthorizedException;
import com.paysi.identity.domain.Account;
import com.paysi.identity.domain.AccountStatus;
import com.paysi.identity.domain.InitialMode;
import com.paysi.identity.domain.KycStatus;
import com.paysi.identity.domain.PayoutDelay;
import com.paysi.identity.domain.PersonType;
import com.paysi.identity.domain.TaxId;
import com.paysi.identity.port.AccountRepository;
import com.paysi.identity.port.PasswordHasher;
import com.paysi.identity.session.domain.UserSession;
import com.paysi.identity.session.port.SessionStore;
import com.paysi.identity.session.port.SessionTokenService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SessionServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-26T12:00:00Z");

    @Test
    void logsInRenewsAndSwitchesModeWithoutChangingToken() {
        var store = new MemoryStore();
        var service = service(activeAccount(), store);

        var login = service.login(" USER@EXAMPLE.COM ", "correct", InitialMode.SELLER);
        var renewed = service.authenticate(login.rawToken());
        var switched = service.switchMode(login.rawToken(), InitialMode.AFFILIATE);

        assertThat(login.rawToken()).isEqualTo("raw-token");
        assertThat(renewed.session().expiresAt()).isEqualTo(NOW.plusSeconds(12 * 60 * 60));
        assertThat(switched.rawToken()).isEqualTo(login.rawToken());
        assertThat(switched.session().activeMode()).isEqualTo(InitialMode.AFFILIATE);
        assertThat(store.values).containsKey("hash:raw-token");
    }

    @Test
    void returnsSameGenericErrorForUnknownEmailAndWrongPassword() {
        var unknown = service(null, new MemoryStore());
        var wrongPassword = service(activeAccount(), new MemoryStore());

        assertThatThrownBy(() -> unknown.login("nobody@example.com", "anything", InitialMode.SELLER))
                .isInstanceOfSatisfying(UnauthorizedException.class,
                        error -> assertThat(error.code()).isEqualTo("INVALID_CREDENTIALS"));
        assertThatThrownBy(() -> wrongPassword.login("user@example.com", "wrong", InitialMode.SELLER))
                .isInstanceOfSatisfying(UnauthorizedException.class,
                        error -> assertThat(error.code()).isEqualTo("INVALID_CREDENTIALS"));
    }

    @Test
    void rejectsSuspendedAccount() {
        var service = service(account(AccountStatus.SUSPENDED), new MemoryStore());
        assertThatThrownBy(() -> service.login("user@example.com", "correct", InitialMode.SELLER))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void removesExpiredSessionAndLogoutInvalidatesCurrentSession() {
        var store = new MemoryStore();
        store.values.put("hash:expired", new UserSession(UUID.randomUUID(), InitialMode.SELLER,
                NOW.minusSeconds(50_000), NOW.minusSeconds(1)));
        var service = service(activeAccount(), store);

        assertThatThrownBy(() -> service.authenticate("expired")).isInstanceOf(UnauthorizedException.class);
        assertThat(store.values).doesNotContainKey("hash:expired");

        var login = service.login("user@example.com", "correct", InitialMode.SELLER);
        service.logout(login.rawToken());
        assertThatThrownBy(() -> service.authenticate(login.rawToken())).isInstanceOf(UnauthorizedException.class);
    }

    private static SessionService service(Account account, MemoryStore store) {
        AccountRepository accounts = new AccountRepository() {
            public boolean existsActiveByEmail(String email) { return false; }
            public boolean existsActiveByTaxId(String taxId) { return false; }
            public Optional<Account> findOpenByEmail(String email) { return Optional.ofNullable(account); }
            public void insert(Account ignored) { }
        };
        PasswordHasher passwords = new PasswordHasher() {
            public String hash(String raw) { return "hash"; }
            public boolean matches(String raw, String encoded) { return "correct".equals(raw); }
        };
        SessionTokenService tokens = new SessionTokenService() {
            public String generate() { return "raw-token"; }
            public String hash(String raw) { return "hash:" + raw; }
        };
        return new SessionService(accounts, passwords, store, tokens, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static Account activeAccount() {
        return account(AccountStatus.ACTIVE);
    }

    private static Account account(AccountStatus status) {
        return Account.reconstitute(UUID.randomUUID(), "user@example.com", "encoded", "User",
                PersonType.PF, new TaxId("52998224725"), KycStatus.PENDING, PayoutDelay.D32, 0,
                status, NOW.minusSeconds(3600));
    }

    private static final class MemoryStore implements SessionStore {
        private final Map<String, UserSession> values = new HashMap<>();
        public void save(String hash, UserSession session) { values.put(hash, session); }
        public Optional<UserSession> find(String hash) { return Optional.ofNullable(values.get(hash)); }
        public void delete(String hash) { values.remove(hash); }
        public void deleteAllForAccount(UUID accountId) {
            values.entrySet().removeIf(entry -> entry.getValue().accountId().equals(accountId));
        }
    }
}
