package com.paysi.payout.app;

import com.paysi.core.error.*;
import com.paysi.ledger.app.*;
import com.paysi.ledger.domain.*;
import com.paysi.payout.domain.BankAccount;
import com.paysi.payout.port.*;
import com.paysi.security.mfa.app.MfaGuard;
import com.paysi.security.mfa.domain.SensitiveOperation;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PayoutServiceTest {
    private static final UUID ACCOUNT=UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID BANK=UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID CHALLENGE=UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Test void createsTransferReceiptAndBalancedLedgerEntry(){
        var f=fixture(1000);when(f.repository.lockBank(BANK)).thenReturn(Optional.of(bank(ACCOUNT,true)));when(f.repository.insertPayout(any(),eq(ACCOUNT),eq(500L),eq(BANK),eq("key"))).thenReturn(true);
        var result=f.service.request(ACCOUNT,500,BANK,"key",null);
        assertThat(result.status()).isEqualTo("SENT");assertThat(result.receiptUrl()).isEqualTo("https://receipt/one");
        var command=org.mockito.ArgumentCaptor.forClass(LedgerCommand.class);verify(f.ledger).write(command.capture());assertThat(command.getValue().entries()).hasSize(2);verify(f.repository).markSent(result.payoutId(),"transfer-one","https://receipt/one");
    }

    @Test void replayDoesNotMoveMoneyOrCallProviderTwice(){
        var f=fixture(1000);when(f.repository.findPayout(ACCOUNT,"key")).thenReturn(Optional.of(new PayoutRepository.PayoutState(CHALLENGE,500,BANK,"SENT","https://receipt/one")));
        assertThat(f.service.request(ACCOUNT,500,BANK,"key",null).idempotentReplay()).isTrue();verifyNoInteractions(f.ledger,f.provider);
    }

    @Test void blocksDebtAndCrossAccountBank(){
        var f=fixture(1000);when(f.repository.lockBank(BANK)).thenReturn(Optional.of(bank(ACCOUNT,true)));when(f.repository.lockAndReadDebt(ACCOUNT)).thenReturn(-1L);
        assertThatThrownBy(()->f.service.request(ACCOUNT,500,BANK,"key",null)).isInstanceOf(ConflictException.class);
        when(f.repository.lockBank(BANK)).thenReturn(Optional.of(bank(CHALLENGE,true)));assertThatThrownBy(()->f.service.request(ACCOUNT,500,BANK,"other",null)).isInstanceOf(ValidationException.class);
    }

    @Test void requiresMfaAtConfiguredThreshold(){
        var f=fixture(1000);when(f.repository.lockBank(BANK)).thenReturn(Optional.of(bank(ACCOUNT,true)));when(f.repository.insertPayout(any(),eq(ACCOUNT),eq(1000L),eq(BANK),eq("key"))).thenReturn(true);
        f.service.request(ACCOUNT,1000,BANK,"key",CHALLENGE);verify(f.mfa).consume(ACCOUNT,CHALLENGE,SensitiveOperation.PAYOUT);
    }

    @Test void rejectsUnverifiedOrArchivedBankAccount(){
        var f=fixture(1000);
        when(f.repository.lockBank(BANK)).thenReturn(Optional.of(bank(ACCOUNT,false)));
        assertThatThrownBy(()->f.service.request(ACCOUNT,500,BANK,"unverified",null)).isInstanceOf(ValidationException.class);

        var archived=new BankAccount(BANK,ACCOUNT,"001","1","1234","52998224725","PF","User","CHECKING","CPF",Instant.now(),Instant.now());
        when(f.repository.lockBank(BANK)).thenReturn(Optional.of(archived));
        assertThatThrownBy(()->f.service.request(ACCOUNT,500,BANK,"archived",null)).isInstanceOf(ValidationException.class);
        verifyNoInteractions(f.ledger,f.provider);
    }

    @Test void rejectsAmountBelowMinimumAndChangedIdempotentRequest(){
        var f=fixture(1000);
        assertThatThrownBy(()->f.service.request(ACCOUNT,199,BANK,"small",null)).isInstanceOf(ValidationException.class);
        when(f.repository.findPayout(ACCOUNT,"key")).thenReturn(Optional.of(new PayoutRepository.PayoutState(CHALLENGE,500,BANK,"SENT","https://receipt/one")));
        assertThatThrownBy(()->f.service.request(ACCOUNT,600,BANK,"key",null)).isInstanceOf(ConflictException.class);
        verifyNoInteractions(f.ledger,f.provider);
    }

    private static Fixture fixture(long threshold){var repository=mock(PayoutRepository.class);var ledger=mock(LedgerService.class);var mfa=mock(MfaGuard.class);var provider=mock(PayoutProvider.class);when(ledger.write(any())).thenReturn(new LedgerWriteResult(CHALLENGE,false));when(provider.requestPix(any(),anyLong(),any())).thenReturn(new PayoutProvider.TransferResult("transfer-one","https://receipt/one"));return new Fixture(new PayoutService(repository,ledger,mfa,provider,threshold),repository,ledger,mfa,provider);}
    private static BankAccount bank(UUID owner,boolean valid){return new BankAccount(BANK,owner,"001","1","1234","52998224725","PF","User","CHECKING","CPF",valid?Instant.now():null,null);}
    private record Fixture(PayoutService service,PayoutRepository repository,LedgerService ledger,MfaGuard mfa,PayoutProvider provider){}
}
