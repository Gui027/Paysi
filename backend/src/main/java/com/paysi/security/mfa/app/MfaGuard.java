package com.paysi.security.mfa.app;
import com.paysi.core.error.ForbiddenException;
import com.paysi.security.mfa.domain.SensitiveOperation;
import com.paysi.security.mfa.port.MfaStore;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.util.UUID;
@Component
public class MfaGuard {
    private final MfaStore store; private final Clock clock=Clock.systemUTC();
    public MfaGuard(MfaStore store){this.store=store;}
    @Transactional public void consume(UUID accountId,UUID challengeId,SensitiveOperation operation){if(!store.consumeVerified(challengeId,accountId,operation.name(),clock.instant()))throw new ForbiddenException("MFA_CHALLENGE_INVALID","Desafio ausente, expirado ou incompatível com a operação");}
}
