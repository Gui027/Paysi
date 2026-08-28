package com.paysi.admin.app;

import com.paysi.admin.domain.AdminPrincipal;
import com.paysi.admin.port.AdminRepository;
import com.paysi.core.error.ForbiddenException;
import com.paysi.core.error.UnauthorizedException;
import com.paysi.identity.port.PasswordHasher;
import com.paysi.security.mfa.adapter.TotpCodes;
import com.paysi.security.mfa.port.SecretProtector;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Set;

@Service
public class AdminAuthService {
    private final AdminRepository repository;
    private final PasswordHasher passwords;
    private final SecretProtector protector;
    private final TotpCodes totp;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public AdminAuthService(AdminRepository repository, PasswordHasher passwords,
                            SecretProtector protector, TotpCodes totp) {
        this(repository, passwords, protector, totp, Clock.systemUTC());
    }

    AdminAuthService(AdminRepository repository, PasswordHasher passwords,
                     SecretProtector protector, TotpCodes totp, Clock clock) {
        this.repository = repository;
        this.passwords = passwords;
        this.protector = protector;
        this.totp = totp;
        this.clock = clock;
    }

    public AdminPrincipal authenticate(String authorization, String code, Set<String> roles) {
        var basic = basicCredentials(authorization);
        if (code == null || code.isBlank()) {
            throw invalid();
        }
        var credential = repository.credential(basic.email())
                .orElseThrow(AdminAuthService::invalid);
        if (!passwords.matches(basic.password(), credential.passwordHash())
                || !totp.matches(protector.decrypt(credential.mfaSecretEncrypted()), code, clock.instant())) {
            throw invalid();
        }
        if (!credential.role().equals("ADMIN") && !roles.contains(credential.role())) {
            throw new ForbiddenException("ADMIN_ROLE_REQUIRED", "Papel administrativo insuficiente");
        }
        return new AdminPrincipal(credential.id(), credential.role());
    }

    private static UnauthorizedException invalid() {
        return new UnauthorizedException("ADMIN_CREDENTIALS_INVALID", "Credenciais administrativas inválidas");
    }

    private static BasicCredentials basicCredentials(String authorization) {
        if (authorization == null || !authorization.startsWith("Basic ")) throw invalid();
        try {
            String decoded = new String(Base64.getDecoder().decode(authorization.substring(6)),
                    StandardCharsets.UTF_8);
            int separator = decoded.indexOf(':');
            if (separator <= 0 || separator == decoded.length() - 1) throw invalid();
            return new BasicCredentials(decoded.substring(0, separator).strip().toLowerCase(),
                    decoded.substring(separator + 1));
        } catch (IllegalArgumentException exception) {
            throw invalid();
        }
    }

    private record BasicCredentials(String email, String password) {}
}
