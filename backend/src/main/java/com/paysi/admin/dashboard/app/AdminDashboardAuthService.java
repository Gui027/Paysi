package com.paysi.admin.dashboard.app;

import com.paysi.admin.dashboard.port.AdminDashboardRepository;
import com.paysi.core.error.TooManyRequestsException;
import com.paysi.core.error.UnauthorizedException;
import com.paysi.identity.port.PasswordHasher;
import com.paysi.identity.session.port.SessionTokenService;
import com.paysi.security.ratelimit.port.RateLimiter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

@Service
public class AdminDashboardAuthService {
    public static final Duration SESSION_TTL = Duration.ofHours(12);
    private static final Duration LOGIN_WINDOW = Duration.ofMinutes(15);

    private final AdminDashboardRepository repository;
    private final PasswordHasher passwords;
    private final SessionTokenService tokens;
    private final RateLimiter rateLimiter;
    private final String configuredUsername;
    private final String configuredPasswordHash;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public AdminDashboardAuthService(AdminDashboardRepository repository, PasswordHasher passwords,
                                     SessionTokenService tokens, RateLimiter rateLimiter,
                                     @Value("${paysi.admin-dashboard.username}") String configuredUsername,
                                     @Value("${paysi.admin-dashboard.password-hash}") String configuredPasswordHash) {
        this(repository, passwords, tokens, rateLimiter, configuredUsername, configuredPasswordHash,
                Clock.systemUTC());
    }

    AdminDashboardAuthService(AdminDashboardRepository repository, PasswordHasher passwords,
                              SessionTokenService tokens, RateLimiter rateLimiter, String configuredUsername,
                              String configuredPasswordHash, Clock clock) {
        this.repository = repository;
        this.passwords = passwords;
        this.tokens = tokens;
        this.rateLimiter = rateLimiter;
        this.configuredUsername = normalize(configuredUsername);
        this.configuredPasswordHash = configuredPasswordHash;
        this.clock = clock;
    }

    public AuthenticatedAdmin login(String username, String password, String remoteIp) {
        String normalized = normalize(username);
        guard(remoteIp, normalized);
        boolean usernameMatches = MessageDigest.isEqual(normalized.getBytes(StandardCharsets.UTF_8),
                configuredUsername.getBytes(StandardCharsets.UTF_8));
        boolean passwordMatches = password != null && passwords.matches(password, configuredPasswordHash);
        if (!usernameMatches || !passwordMatches) throw invalid();

        Instant now = clock.instant();
        repository.deleteExpiredSessions(now);
        String rawToken = tokens.generate();
        repository.saveSession(tokens.hash(rawToken), configuredUsername, now, now.plus(SESSION_TTL));
        return new AuthenticatedAdmin(configuredUsername, rawToken);
    }

    public AuthenticatedAdmin authenticate(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) throw invalidSession();
        String hash = tokens.hash(rawToken);
        var stored = repository.findSession(hash).orElseThrow(AdminDashboardAuthService::invalidSession);
        Instant now = clock.instant();
        if (!stored.expiresAt().isAfter(now)) {
            repository.deleteSession(hash);
            throw invalidSession();
        }
        repository.renewSession(hash, now, now.plus(SESSION_TTL));
        return new AuthenticatedAdmin(stored.username(), rawToken);
    }

    public void logout(String rawToken) {
        if (rawToken != null && !rawToken.isBlank()) repository.deleteSession(tokens.hash(rawToken));
    }

    private void guard(String remoteIp, String username) {
        String ip = remoteIp == null || remoteIp.isBlank() ? "unknown" : remoteIp;
        if (!rateLimiter.allow("admin-dashboard:login:ip:" + ip, 5, LOGIN_WINDOW)
                || !rateLimiter.allow("admin-dashboard:login:user:" + username, 5, LOGIN_WINDOW)) {
            throw new TooManyRequestsException("ADMIN_LOGIN_RATE_LIMITED",
                    "Muitas tentativas de acesso. Aguarde antes de tentar novamente", null);
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
    }

    private static UnauthorizedException invalid() {
        return new UnauthorizedException("ADMIN_CREDENTIALS_INVALID", "Usuário ou senha inválidos");
    }

    private static UnauthorizedException invalidSession() {
        return new UnauthorizedException("ADMIN_SESSION_INVALID", "Sessão administrativa ausente ou expirada");
    }

    public record AuthenticatedAdmin(String username, String rawToken) { }
}
