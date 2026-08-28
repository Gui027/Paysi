package com.paysi.identity.session.app;

import com.paysi.core.error.ForbiddenException;
import com.paysi.core.error.UnauthorizedException;
import com.paysi.identity.domain.AccountStatus;
import com.paysi.identity.domain.InitialMode;
import com.paysi.identity.port.AccountRepository;
import com.paysi.identity.port.PasswordHasher;
import com.paysi.identity.session.domain.UserSession;
import com.paysi.identity.session.port.SessionStore;
import com.paysi.identity.session.port.SessionTokenService;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

@Service
public class SessionService {
    static final Duration INACTIVITY_TIMEOUT = Duration.ofHours(12);
    private static final String AUTH_MESSAGE = "E-mail ou senha inválidos";

    private final AccountRepository accounts;
    private final PasswordHasher passwordHasher;
    private final SessionStore sessions;
    private final SessionTokenService tokens;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public SessionService(AccountRepository accounts, PasswordHasher passwordHasher, SessionStore sessions,
                          SessionTokenService tokens) {
        this(accounts, passwordHasher, sessions, tokens, Clock.systemUTC());
    }

    SessionService(AccountRepository accounts, PasswordHasher passwordHasher, SessionStore sessions,
                   SessionTokenService tokens, Clock clock) {
        this.accounts = accounts;
        this.passwordHasher = passwordHasher;
        this.sessions = sessions;
        this.tokens = tokens;
        this.clock = clock;
    }

    public AuthenticatedSession login(String email, String password, InitialMode initialMode) {
        String normalizedEmail = email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
        var account = accounts.findOpenByEmail(normalizedEmail)
                .filter(found -> passwordHasher.matches(password, found.passwordHash()))
                .orElseThrow(() -> new UnauthorizedException("INVALID_CREDENTIALS", AUTH_MESSAGE));

        if (account.status() == AccountStatus.SUSPENDED) {
            throw new ForbiddenException("ACCOUNT_UNAVAILABLE", "Não foi possível autenticar esta conta");
        }

        Instant now = clock.instant();
        String rawToken = tokens.generate();
        var session = new UserSession(account.id(), initialMode, now, now.plus(INACTIVITY_TIMEOUT));
        sessions.save(tokens.hash(rawToken), session);
        return new AuthenticatedSession(rawToken, SessionView.from(session));
    }

    public AuthenticatedSession authenticate(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) throw invalidSession();
        String hash = tokens.hash(rawToken);
        UserSession current = sessions.find(hash).orElseThrow(SessionService::invalidSession);
        Instant now = clock.instant();
        if (current.expiredAt(now)) {
            sessions.delete(hash);
            throw invalidSession();
        }
        UserSession renewed = current.renew(now, now.plus(INACTIVITY_TIMEOUT));
        sessions.save(hash, renewed);
        return new AuthenticatedSession(rawToken, SessionView.from(renewed));
    }

    public AuthenticatedSession switchMode(String rawToken, InitialMode mode) {
        AuthenticatedSession authenticated = authenticate(rawToken);
        Instant now = clock.instant();
        String hash = tokens.hash(rawToken);
        var changed = new UserSession(authenticated.session().accountId(), mode, now,
                now.plus(INACTIVITY_TIMEOUT));
        sessions.save(hash, changed);
        return new AuthenticatedSession(rawToken, SessionView.from(changed));
    }

    public void logout(String rawToken) {
        if (rawToken != null && !rawToken.isBlank()) sessions.delete(tokens.hash(rawToken));
    }

    private static UnauthorizedException invalidSession() {
        return new UnauthorizedException("SESSION_INVALID", "Sessão ausente ou expirada");
    }
}
