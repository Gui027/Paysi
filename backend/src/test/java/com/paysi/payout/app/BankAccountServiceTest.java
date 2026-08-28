package com.paysi.payout.app;

import com.paysi.core.error.ValidationException;
import com.paysi.payout.domain.*;
import com.paysi.payout.port.*;
import com.paysi.security.mfa.app.MfaGuard;
import com.paysi.security.mfa.domain.SensitiveOperation;
import com.paysi.security.mfa.port.SecretProtector;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class BankAccountServiceTest {
    private static final UUID ACCOUNT=UUID.fromString("11111111-1111-1111-1111-111111111111"), CHALLENGE=UUID.fromString("22222222-2222-2222-2222-222222222222");
    @Test void verifiesOwnershipEncryptsSensitiveFieldsAndRequiresMfa(){
        var repository=mock(PayoutRepository.class);var provider=mock(BankVerificationProvider.class);var protector=mock(SecretProtector.class);var mfa=mock(MfaGuard.class);var command=command("52998224725");
        when(repository.accountHolder(ACCOUNT)).thenReturn(Optional.of(new PayoutRepository.AccountHolder("52998224725","User")));when(provider.verifyOwnership(command,"52998224725")).thenReturn(true);when(protector.encrypt(any())).thenAnswer(call->call.getArgument(0));
        var bank=new BankAccountService(repository,provider,protector,mfa).create(ACCOUNT,command,CHALLENGE);
        assertThat(bank.numberLast4()).isEqualTo("6789");verify(mfa).consume(ACCOUNT,CHALLENGE,SensitiveOperation.BANK_ACCOUNT_CHANGE);verify(repository).insertBank(eq(bank),any(),any());
    }
    @Test void rejectsAnotherHolderBeforePersisting(){
        var repository=mock(PayoutRepository.class);when(repository.accountHolder(ACCOUNT)).thenReturn(Optional.of(new PayoutRepository.AccountHolder("52998224725","User")));var service=new BankAccountService(repository,mock(BankVerificationProvider.class),mock(SecretProtector.class),mock(MfaGuard.class));
        assertThatThrownBy(()->service.create(ACCOUNT,command("11144477735"),CHALLENGE)).isInstanceOf(ValidationException.class);verify(repository,never()).insertBank(any(),any(),any());
    }
    private static BankAccountCommand command(String taxId){return new BankAccountCommand("PF",taxId,"User","001","1234","12345678","9","CHECKING","CPF",taxId);}
}
