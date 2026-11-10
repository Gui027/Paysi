package com.paysi.admin.dashboard.app;

import com.paysi.admin.dashboard.port.AdminDashboardRepository;
import com.paysi.core.error.TooManyRequestsException;
import com.paysi.core.error.UnauthorizedException;
import com.paysi.identity.port.PasswordHasher;
import com.paysi.identity.session.port.SessionTokenService;
import com.paysi.security.ratelimit.port.RateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AdminDashboardAuthServiceTest {
    private final AdminDashboardRepository repository = mock(AdminDashboardRepository.class);
    private final PasswordHasher passwords = mock(PasswordHasher.class);
    private final SessionTokenService tokens = mock(SessionTokenService.class);
    private final RateLimiter limiter = mock(RateLimiter.class);
    private final Instant now = Instant.parse("2026-11-10T15:00:00Z");
    private AdminDashboardAuthService service;

    @BeforeEach
    void setUp() {
        service = new AdminDashboardAuthService(repository, passwords, tokens, limiter, "admin", "hash",
                Clock.fixed(now, ZoneOffset.UTC));
        when(limiter.allow(anyString(), anyInt(), any())).thenReturn(true);
        when(tokens.generate()).thenReturn("raw-token");
        when(tokens.hash("raw-token")).thenReturn("token-hash");
    }

    @Test
    void logsInWithConfiguredCredentialsAndStoresOnlyTokenHash() {
        when(passwords.matches("Siad3797", "hash")).thenReturn(true);

        var authenticated = service.login(" ADMIN ", "Siad3797", "127.0.0.1");

        assertThat(authenticated.username()).isEqualTo("admin");
        assertThat(authenticated.rawToken()).isEqualTo("raw-token");
        verify(repository).saveSession("token-hash", "admin", now,
                now.plus(AdminDashboardAuthService.SESSION_TTL));
    }

    @Test
    void rejectsInvalidCredentialsAndRateLimitedAttempts() {
        when(passwords.matches(anyString(), eq("hash"))).thenReturn(true);
        assertThatThrownBy(() -> service.login("other", "Siad3797", "127.0.0.1"))
                .isInstanceOf(UnauthorizedException.class);

        when(limiter.allow(startsWith("admin-dashboard:login:ip:"), anyInt(), any())).thenReturn(false);
        assertThatThrownBy(() -> service.login("admin", "Siad3797", "127.0.0.1"))
                .isInstanceOf(TooManyRequestsException.class);
    }

    @Test
    void renewsValidSessionAndDeletesExpiredSession() {
        when(repository.findSession("token-hash")).thenReturn(Optional.of(
                new AdminDashboardRepository.StoredSession("admin", now.plusSeconds(60))));
        assertThat(service.authenticate("raw-token").username()).isEqualTo("admin");
        verify(repository).renewSession("token-hash", now, now.plus(AdminDashboardAuthService.SESSION_TTL));

        reset(repository);
        when(repository.findSession("token-hash")).thenReturn(Optional.of(
                new AdminDashboardRepository.StoredSession("admin", now)));
        assertThatThrownBy(() -> service.authenticate("raw-token")).isInstanceOf(UnauthorizedException.class);
        verify(repository).deleteSession("token-hash");
    }
}
