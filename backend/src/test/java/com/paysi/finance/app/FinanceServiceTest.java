package com.paysi.finance.app;

import com.paysi.core.error.ConflictException;
import com.paysi.core.error.ForbiddenException;
import com.paysi.core.error.ValidationException;
import com.paysi.finance.app.FinanceModels.AccountRow;
import com.paysi.finance.app.FinanceModels.BankRow;
import com.paysi.finance.port.FinanceRepository;
import com.paysi.identity.port.PlatformPlanReader;
import com.paysi.ledger.query.app.BalanceView;
import com.paysi.ledger.query.app.LedgerQueryService;
import com.paysi.payout.domain.BankAccount;
import com.paysi.payout.port.PayoutRepository;
import com.paysi.security.mfa.app.MfaGuard;
import com.paysi.security.mfa.domain.MfaCredential;
import com.paysi.security.mfa.domain.SensitiveOperation;
import com.paysi.security.mfa.port.MfaStore;
import com.paysi.security.mfa.port.SecretProtector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FinanceServiceTest {
    private static final UUID ACCOUNT = UUID.randomUUID();
    private static final UUID CHALLENGE = UUID.randomUUID();
    private static final UUID OLD_BANK = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-09-26T12:00:00Z");
    private static final String CPF = "52998224725";
    private static final String CNPJ = "11222333000181";

    private FinanceRepository repository;
    private PayoutRepository payouts;
    private MfaGuard mfa;
    private MfaStore mfaStore;
    private LedgerQueryService ledger;
    private FinanceService service;

    @BeforeEach
    void setUp() {
        repository = mock(FinanceRepository.class);
        payouts = mock(PayoutRepository.class);
        mfa = mock(MfaGuard.class);
        mfaStore = mock(MfaStore.class);
        ledger = mock(LedgerQueryService.class);
        SecretProtector protector = new SecretProtector() {
            public byte[] encrypt(byte[] clear) { return clear; }
            public byte[] decrypt(byte[] encrypted) { return encrypted; }
        };
        service = new FinanceService(repository, payouts, protector, mfa, mfaStore, ledger,
                mock(PlatformPlanReader.class), 100_000, Clock.fixed(NOW, ZoneOffset.UTC));
        when(repository.account(ACCOUNT)).thenReturn(Optional.of(new AccountRow("Guilherme Rodrigues", "PF", CPF, "APPROVED", "D32")));
    }

    private void mfaEnabled(boolean enabled) {
        var credential = mock(MfaCredential.class);
        when(credential.enabled()).thenReturn(enabled);
        when(mfaStore.credential(ACCOUNT)).thenReturn(Optional.of(credential));
    }

    @Test
    void pixKeyTypesAreRecognizedAndNormalized() {
        assertThat(PixKeys.detect(" 529.982.247-25 ")).isEqualTo(new PixKeys.Detected("CPF", CPF));
        assertThat(PixKeys.detect("11.222.333/0001-81")).isEqualTo(new PixKeys.Detected("CNPJ", CNPJ));
        assertThat(PixKeys.detect("Maria@Exemplo.com")).isEqualTo(new PixKeys.Detected("EMAIL", "maria@exemplo.com"));
        assertThat(PixKeys.detect("(27) 99951-3505")).isEqualTo(new PixKeys.Detected("PHONE", "+5527999513505"));
        assertThat(PixKeys.detect("+55 27 99951-3505")).isEqualTo(new PixKeys.Detected("PHONE", "+5527999513505"));
        assertThat(PixKeys.detect("123E4567-E89B-12D3-A456-426614174000").type()).isEqualTo("EVP");
        for (String invalid : new String[]{"", "abc", "123", "a@b", "x".repeat(200)}) {
            assertThatThrownBy(() -> PixKeys.detect(invalid)).as(invalid).isInstanceOf(ValidationException.class);
        }
    }

    @Test
    void firstPixKeyNeedsNoMfaAndIsStoredEncrypted() {
        when(repository.activeBankIds(ACCOUNT)).thenReturn(List.of());

        var saved = service.savePixKey(ACCOUNT, CPF, null);

        assertThat(saved.keyType()).isEqualTo("CPF");
        var bank = ArgumentCaptor.forClass(BankAccount.class);
        verify(payouts).insertBank(bank.capture(), any(), eq(CPF.getBytes(StandardCharsets.UTF_8)));
        assertThat(bank.getValue().holderTaxId()).isEqualTo(CPF);
        assertThat(bank.getValue().pixKeyType()).isEqualTo("CPF");
        verify(mfa, never()).consume(any(), any(), any());
    }

    @Test
    void changingAnExistingKeyRequiresMfaAndArchivesTheOldAccount() {
        when(repository.activeBankIds(ACCOUNT)).thenReturn(List.of(OLD_BANK));

        mfaEnabled(false);
        assertThatThrownBy(() -> service.savePixKey(ACCOUNT, "maria@exemplo.com", CHALLENGE))
                .isInstanceOfSatisfying(ForbiddenException.class, error -> assertThat(error.code()).isEqualTo("MFA_REQUIRED"));

        mfaEnabled(true);
        assertThatThrownBy(() -> service.savePixKey(ACCOUNT, "maria@exemplo.com", null))
                .isInstanceOfSatisfying(ForbiddenException.class, error -> assertThat(error.code()).isEqualTo("MFA_CHALLENGE_INVALID"));

        service.savePixKey(ACCOUNT, "maria@exemplo.com", CHALLENGE);
        verify(mfa).consume(ACCOUNT, CHALLENGE, SensitiveOperation.BANK_ACCOUNT_CHANGE);
        verify(payouts).archiveBank(eq(ACCOUNT), eq(OLD_BANK), any());
    }

    @Test
    void cpfOrCnpjKeyMustBelongToTheHolder() {
        when(repository.activeBankIds(ACCOUNT)).thenReturn(List.of());
        assertThatThrownBy(() -> service.savePixKey(ACCOUNT, "11.222.333/0001-81", null))
                .isInstanceOfSatisfying(ValidationException.class, error -> assertThat(error.code()).isEqualTo("BANK_HOLDER_MISMATCH"));
        verify(payouts, never()).insertBank(any(), any(), any());
    }

    @Test
    void convertingToCompanyChangesTheDocumentAndPointsPayoutsToTheNewKey() {
        mfaEnabled(true);
        when(repository.activeBankIds(ACCOUNT)).thenReturn(List.of(OLD_BANK));

        var saved = service.convertToCompany(ACCOUNT, "Empresa LTDA", "11.222.333/0001-81", CNPJ, CHALLENGE);

        assertThat(saved.key()).isEqualTo(CNPJ);
        verify(mfa).consume(ACCOUNT, CHALLENGE, SensitiveOperation.BANK_ACCOUNT_CHANGE);
        verify(repository).convertToCompany(ACCOUNT, "Empresa LTDA", CNPJ, "PF", CPF);
        var bank = ArgumentCaptor.forClass(BankAccount.class);
        verify(payouts).insertBank(bank.capture(), any(), any());
        assertThat(bank.getValue().holderTaxId()).isEqualTo(CNPJ);
        assertThat(bank.getValue().holderType()).isEqualTo("PJ");
        verify(payouts).archiveBank(eq(ACCOUNT), eq(OLD_BANK), any());
    }

    @Test
    void conversionRejectsBadInputsAndCompaniesThatAreAlreadyPj() {
        mfaEnabled(true);
        assertThatThrownBy(() -> service.convertToCompany(ACCOUNT, "Ab", CNPJ, CNPJ, CHALLENGE)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> service.convertToCompany(ACCOUNT, "Empresa", "11111111111111", CNPJ, CHALLENGE)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> service.convertToCompany(ACCOUNT, "Empresa", CNPJ, CPF, CHALLENGE))
                .isInstanceOfSatisfying(ValidationException.class, error -> assertThat(error.code()).isEqualTo("BANK_HOLDER_MISMATCH"));
        verify(repository, never()).convertToCompany(any(), any(), any(), any(), any());

        when(repository.account(ACCOUNT)).thenReturn(Optional.of(new AccountRow("Empresa", "PJ", CNPJ, "APPROVED", "D32")));
        assertThatThrownBy(() -> service.convertToCompany(ACCOUNT, "Empresa", CNPJ, CNPJ, CHALLENGE)).isInstanceOf(ConflictException.class);
    }

    @Test
    void overviewGroupsGuaranteeAndPendingAsPendingBalance() {
        when(ledger.balance(ACCOUNT)).thenReturn(new BalanceView(1_000, 3_985, 500, 8_367, 0, NOW));
        when(repository.activeBank(ACCOUNT)).thenReturn(Optional.of(new BankRow(OLD_BANK, "Guilherme", "CPF", CPF.getBytes(StandardCharsets.UTF_8), NOW)));
        mfaEnabled(false);

        var overview = service.overview(ACCOUNT);

        assertThat(overview.balance().availableCents()).isEqualTo(8_367);
        assertThat(overview.balance().pendingCents()).isEqualTo(4_985);
        assertThat(overview.pix().key()).isEqualTo(CPF);
        assertThat(overview.mfaEnabled()).isFalse();
        assertThat(overview.minPayoutCents()).isEqualTo(200);
        assertThat(overview.holder().country()).isEqualTo("BR");
    }

    @Test
    void feesShowPlanRatesPayoutDelayAndReserve() {
        var fees = service.fees(ACCOUNT);
        assertThat(fees.plan()).isEqualTo("TRANSACIONAL");
        assertThat(fees.payoutDelayDays()).isEqualTo(32);
        assertThat(fees.reserveBps()).isEqualTo(400);
        assertThat(fees.reserveDays()).isEqualTo(90);
        assertThat(fees.methods()).anySatisfy(fee -> {
            assertThat(fee.method()).isEqualTo("PIX");
            assertThat(fee.feeBps()).isEqualTo(399);
            assertThat(fee.fixedCents()).isEqualTo(200);
        });
    }
}
