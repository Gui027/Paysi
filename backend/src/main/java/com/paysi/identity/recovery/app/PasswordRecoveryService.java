package com.paysi.identity.recovery.app;

import com.paysi.core.error.ValidationException;
import com.paysi.identity.domain.AccountStatus;
import com.paysi.identity.port.AccountRepository;
import com.paysi.identity.port.PasswordHasher;
import com.paysi.identity.recovery.port.PasswordResetTokenStore;
import com.paysi.identity.recovery.port.RecoveryMailSender;
import com.paysi.identity.recovery.port.RecoveryTokenService;
import com.paysi.identity.session.port.SessionStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

@Service
public class PasswordRecoveryService {
    static final Duration TOKEN_LIFETIME = Duration.ofHours(1);
    private static final Logger LOG = LoggerFactory.getLogger(PasswordRecoveryService.class);

    private final AccountRepository accounts;
    private final PasswordResetTokenStore resetTokens;
    private final RecoveryTokenService tokens;
    private final RecoveryMailSender mailSender;
    private final PasswordHasher passwordHasher;
    private final SessionStore sessions;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public PasswordRecoveryService(AccountRepository accounts, PasswordResetTokenStore resetTokens,
                                   RecoveryTokenService tokens, RecoveryMailSender mailSender,
                                   PasswordHasher passwordHasher, SessionStore sessions) {
        this(accounts, resetTokens, tokens, mailSender, passwordHasher, sessions, Clock.systemUTC());
    }

    PasswordRecoveryService(AccountRepository accounts, PasswordResetTokenStore resetTokens,
                            RecoveryTokenService tokens, RecoveryMailSender mailSender,
                            PasswordHasher passwordHasher, SessionStore sessions, Clock clock) {
        this.accounts = accounts;
        this.resetTokens = resetTokens;
        this.tokens = tokens;
        this.mailSender = mailSender;
        this.passwordHasher = passwordHasher;
        this.sessions = sessions;
        this.clock = clock;
    }

    @Transactional
    public void request(String email) {
        String normalized = email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
        accounts.findOpenByEmail(normalized)
                .filter(account -> account.status() == AccountStatus.ACTIVE || account.status() == AccountStatus.LIMITED)
                .ifPresent(account -> {
                    Instant now = clock.instant();
                    String rawToken = tokens.generate();
                    resetTokens.create(account.id(), tokens.hash(rawToken), now.plus(TOKEN_LIFETIME), now);
                    try {
                        mailSender.sendPasswordReset(account.email(), rawToken);
                    } catch (RuntimeException ex) {
                        // A resposta pública permanece idêntica e não registra o e-mail.
                        LOG.warn("Falha ao enviar e-mail de recuperação; resposta pública preservada");
                    }
                });
    }

    @Transactional
    public void reset(String rawToken, String newPassword, String confirmation) {
        if (!newPassword.equals(confirmation)) {
            throw new ValidationException("PASSWORD_CONFIRMATION_MISMATCH", "As senhas precisam ser iguais",
                    "confirmPassword");
        }
        Instant now = clock.instant();
        var token = resetTokens.findForUpdate(tokens.hash(rawToken))
                .filter(found -> found.usableAt(now))
                .orElseThrow(PasswordRecoveryService::invalidToken);
        var account = accounts.findById(token.accountId())
                .filter(found -> found.status() == AccountStatus.ACTIVE || found.status() == AccountStatus.LIMITED)
                .orElseThrow(PasswordRecoveryService::invalidToken);

        accounts.updatePassword(account.id(), passwordHasher.hash(newPassword));
        resetTokens.markUsed(token.id(), now);
        sessions.deleteAllForAccount(account.id());
    }

    private static ValidationException invalidToken() {
        return new ValidationException("PASSWORD_RESET_TOKEN_INVALID",
                "O link é inválido, expirou ou já foi utilizado", "token");
    }
}
