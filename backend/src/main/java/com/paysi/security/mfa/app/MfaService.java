package com.paysi.security.mfa.app;

import com.paysi.core.error.ConflictException;
import com.paysi.core.error.ForbiddenException;
import com.paysi.core.error.UnauthorizedException;
import com.paysi.security.mfa.adapter.TotpCodes;
import com.paysi.security.mfa.domain.MfaChallenge;
import com.paysi.security.mfa.domain.MfaCredential;
import com.paysi.security.mfa.domain.SensitiveOperation;
import com.paysi.security.mfa.port.MfaStore;
import com.paysi.security.mfa.port.SecretProtector;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import java.util.stream.IntStream;

@Service
public class MfaService {
    private static final Duration CHALLENGE_TTL = Duration.ofMinutes(5);
    private final MfaStore store; private final SecretProtector protector; private final TotpCodes totp;
    private final SecureRandom random; private final Clock clock;
    public MfaService(MfaStore store, SecretProtector protector, TotpCodes totp) { this(store,protector,totp,new SecureRandom(),Clock.systemUTC()); }
    MfaService(MfaStore store, SecretProtector protector, TotpCodes totp, SecureRandom random, Clock clock) { this.store=store;this.protector=protector;this.totp=totp;this.random=random;this.clock=clock; }

    @Transactional
    public MfaEnrollment setup(UUID accountId, String accountLabel) {
        store.credential(accountId).filter(MfaCredential::enabled).ifPresent(value -> { throw new ConflictException("MFA_ALREADY_ENABLED","O segundo fator já está ativo",null); });
        byte[] secret=totp.newSecret();
        List<String> recovery=IntStream.range(0,8).mapToObj(ignored -> recoveryCode()).toList();
        store.saveEnrollment(accountId,protector.encrypt(secret),recovery.stream().map(MfaService::hash).toList());
        String display=totp.displaySecret(secret);
        return new MfaEnrollment(display,"otpauth://totp/Paysi:"+accountLabel+"?secret="+display+"&issuer=Paysi&digits=6&period=30",recovery);
    }

    @Transactional
    public void confirmEnrollment(UUID accountId,String code) {
        var credential=store.credential(accountId).orElseThrow(() -> new ConflictException("MFA_NOT_CONFIGURED","Inicie a configuração do segundo fator",null));
        if (!totp.matches(protector.decrypt(credential.encryptedSecret()),code,clock.instant())) throw invalidCode();
        store.enable(accountId,clock.instant());
    }

    @Transactional
    public MfaChallengeView challenge(UUID accountId,SensitiveOperation operation) {
        requireEnabled(accountId); Instant expiresAt=clock.instant().plus(CHALLENGE_TTL);
        var challenge=new MfaChallenge(UUID.randomUUID(),accountId,operation,expiresAt,0,null,null);
        store.saveChallenge(challenge); return new MfaChallengeView(challenge.id(),operation,expiresAt,false);
    }

    @Transactional
    public MfaChallengeView verify(UUID accountId,UUID challengeId,String code) {
        Instant now=clock.instant(); var challenge=store.lockChallenge(challengeId).orElseThrow(MfaService::invalidChallenge);
        if (!challenge.accountId().equals(accountId)||challenge.expiredAt(now)||!challenge.available()) throw invalidChallenge();
        var credential=requireEnabled(accountId);
        boolean validTotp=totp.matches(protector.decrypt(credential.encryptedSecret()),code,now);
        boolean validRecovery=!validTotp&&store.consumeRecoveryCode(accountId,hash(code));
        if (!validTotp&&!validRecovery) { store.incrementAttempts(challengeId); throw invalidCode(); }
        store.markVerified(challengeId,now); return new MfaChallengeView(challenge.id(),challenge.operation(),challenge.expiresAt(),true);
    }

    private MfaCredential requireEnabled(UUID accountId) { return store.credential(accountId).filter(MfaCredential::enabled).orElseThrow(() -> new ForbiddenException("MFA_REQUIRED","Ative o segundo fator para continuar")); }
    private String recoveryCode() { byte[] value=new byte[10];random.nextBytes(value);String encoded=Base64.getUrlEncoder().withoutPadding().encodeToString(value).toUpperCase(Locale.ROOT);return encoded.substring(0,7)+"-"+encoded.substring(7,14); }
    static String hash(String value) { try { return Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch(NoSuchAlgorithmException exception){throw new IllegalStateException(exception);} }
    private static UnauthorizedException invalidCode(){return new UnauthorizedException("MFA_CODE_INVALID","Código de segundo fator inválido");}
    private static ForbiddenException invalidChallenge(){return new ForbiddenException("MFA_CHALLENGE_INVALID","Desafio ausente, expirado ou já utilizado");}
}
