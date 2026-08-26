package com.paysi.security.mfa.app;

import com.paysi.core.error.ForbiddenException;
import com.paysi.core.error.UnauthorizedException;
import com.paysi.security.mfa.adapter.TotpCodes;
import com.paysi.security.mfa.domain.*;
import com.paysi.security.mfa.port.*;
import org.junit.jupiter.api.Test;
import java.security.SecureRandom;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MfaServiceTest {
    private static final Instant NOW=Instant.parse("2026-08-26T12:00:00Z");
    private static final UUID ACCOUNT=UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final SecretProtector protector=new SecretProtector(){public byte[] encrypt(byte[] value){return value;}public byte[] decrypt(byte[] value){return value;}};

    @Test void enrollsConfirmsAndVerifiesOperationBoundChallenge(){
        var store=new MemoryStore();var totp=mock(TotpCodes.class);when(totp.newSecret()).thenReturn(new byte[]{1,2,3});when(totp.displaySecret(any())).thenReturn("DISPLAY");when(totp.matches(any(),eq("123456"),any())).thenReturn(true);
        var service=service(store,totp);var enrollment=service.setup(ACCOUNT,"account");
        assertThat(enrollment.secret()).isEqualTo("DISPLAY");assertThat(enrollment.recoveryCodes()).hasSize(8);
        service.confirmEnrollment(ACCOUNT,"123456");var challenge=service.challenge(ACCOUNT,SensitiveOperation.PAYOUT);var verified=service.verify(ACCOUNT,challenge.challengeId(),"123456");
        assertThat(verified.verified()).isTrue();assertThat(verified.operation()).isEqualTo(SensitiveOperation.PAYOUT);
    }

    @Test void rejectsExpiredWrongAccountAndLimitsInvalidAttempts(){
        var store=new MemoryStore();store.credential=new MfaCredential(new byte[]{1},NOW.minusSeconds(1));var totp=mock(TotpCodes.class);when(totp.matches(any(),anyString(),any())).thenReturn(false);var service=service(store,totp);
        UUID id=UUID.randomUUID();store.challenge=new MfaChallenge(id,ACCOUNT,SensitiveOperation.PAYOUT,NOW.minusSeconds(1),0,null,null);
        assertThatThrownBy(() -> service.verify(ACCOUNT,id,"000000")).isInstanceOf(ForbiddenException.class);
        store.challenge=new MfaChallenge(id,ACCOUNT,SensitiveOperation.PAYOUT,NOW.plusSeconds(60),0,null,null);
        assertThatThrownBy(() -> service.verify(UUID.randomUUID(),id,"000000")).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service.verify(ACCOUNT,id,"000000")).isInstanceOf(UnauthorizedException.class);
        assertThat(store.attempts).isEqualTo(1);
    }

    @Test void recoveryCodeIsConsumedOnlyOnceAndGuardBindsOperation(){
        var store=new MemoryStore();store.credential=new MfaCredential(new byte[]{1},NOW.minusSeconds(1));var totp=mock(TotpCodes.class);when(totp.matches(any(),anyString(),any())).thenReturn(false);var service=service(store,totp);
        String recovery="RECOVERY-CODE";store.recovery.add(MfaService.hash(recovery));var challenge=service.challenge(ACCOUNT,SensitiveOperation.BANK_ACCOUNT_CHANGE);
        service.verify(ACCOUNT,challenge.challengeId(),recovery);
        assertThat(store.recovery).isEmpty();
        var guard=new MfaGuard(store);
        assertThatThrownBy(() -> guard.consume(ACCOUNT,challenge.challengeId(),SensitiveOperation.PAYOUT)).isInstanceOf(ForbiddenException.class);
        assertThat(store.consumeVerified(challenge.challengeId(),ACCOUNT,SensitiveOperation.BANK_ACCOUNT_CHANGE.name(),NOW)).isTrue();
        assertThat(store.consumeVerified(challenge.challengeId(),ACCOUNT,SensitiveOperation.BANK_ACCOUNT_CHANGE.name(),NOW)).isFalse();
    }

    private MfaService service(MemoryStore store,TotpCodes totp){return new MfaService(store,protector,totp,new SecureRandom(new byte[]{1}),Clock.fixed(NOW,ZoneOffset.UTC));}

    private static final class MemoryStore implements MfaStore {
        MfaCredential credential;MfaChallenge challenge;Set<String> recovery=new HashSet<>();int attempts;
        public Optional<MfaCredential> credential(UUID id){return Optional.ofNullable(credential);}
        public void saveEnrollment(UUID id,byte[] secret,List<String> hashes){credential=new MfaCredential(secret,null);recovery=new HashSet<>(hashes);}
        public void enable(UUID id,Instant at){credential=new MfaCredential(credential.encryptedSecret(),at);}
        public boolean consumeRecoveryCode(UUID id,String hash){return recovery.remove(hash);}
        public void saveChallenge(MfaChallenge value){challenge=value;}
        public Optional<MfaChallenge> lockChallenge(UUID id){return challenge!=null&&challenge.id().equals(id)?Optional.of(challenge):Optional.empty();}
        public void incrementAttempts(UUID id){attempts++;challenge=new MfaChallenge(challenge.id(),challenge.accountId(),challenge.operation(),challenge.expiresAt(),challenge.attempts()+1,challenge.verifiedAt(),challenge.consumedAt());}
        public void markVerified(UUID id,Instant at){challenge=new MfaChallenge(challenge.id(),challenge.accountId(),challenge.operation(),challenge.expiresAt(),challenge.attempts(),at,null);}
        public boolean consumeVerified(UUID id,UUID account,String operation,Instant now){if(challenge==null||!challenge.id().equals(id)||!challenge.accountId().equals(account)||!challenge.operation().name().equals(operation)||challenge.verifiedAt()==null||challenge.consumedAt()!=null||challenge.expiredAt(now))return false;challenge=new MfaChallenge(challenge.id(),challenge.accountId(),challenge.operation(),challenge.expiresAt(),challenge.attempts(),challenge.verifiedAt(),now);return true;}
    }
}
