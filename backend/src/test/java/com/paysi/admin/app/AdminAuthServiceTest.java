package com.paysi.admin.app;

import com.paysi.admin.port.AdminRepository;
import com.paysi.core.error.ForbiddenException;
import com.paysi.core.error.UnauthorizedException;
import com.paysi.identity.port.PasswordHasher;
import com.paysi.security.mfa.adapter.TotpCodes;
import com.paysi.security.mfa.port.SecretProtector;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class AdminAuthServiceTest {
    private static final UUID ADMIN_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Test
    void requiresSeparatePasswordTotpAndRole() {
        var repository = mock(AdminRepository.class);
        var passwords = mock(PasswordHasher.class);
        var protector = mock(SecretProtector.class);
        var totp = mock(TotpCodes.class);
        byte[] encrypted = {1};
        byte[] clear = {2};
        when(repository.credential("admin@paysi.com"))
                .thenReturn(Optional.of(new AdminRepository.Credential(
                        ADMIN_ID, "RISK", "hash", encrypted)));
        when(passwords.matches("secret", "hash")).thenReturn(true);
        when(protector.decrypt(encrypted)).thenReturn(clear);
        Instant now = Instant.parse("2026-08-28T12:00:00Z");
        when(totp.matches(clear, "123456", now)).thenReturn(true);
        var service = new AdminAuthService(repository, passwords, protector, totp,
                Clock.fixed(now, ZoneOffset.UTC));

        assertThat(service.authenticate(basic("ADMIN@PAYSI.COM", "secret"), "123456", Set.of("RISK")).id())
                .isEqualTo(ADMIN_ID);
        assertThatThrownBy(() -> service.authenticate(
                basic("admin@paysi.com", "secret"), "123456", Set.of("SUPPORT")))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service.authenticate(null, null, Set.of("RISK")))
                .isInstanceOf(UnauthorizedException.class);
    }

    private static String basic(String email, String password) {
        return "Basic " + Base64.getEncoder().encodeToString(
                (email + ":" + password).getBytes(StandardCharsets.UTF_8));
    }
}
