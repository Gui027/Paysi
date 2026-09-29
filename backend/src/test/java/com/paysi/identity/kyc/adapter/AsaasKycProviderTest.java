package com.paysi.identity.kyc.adapter;

import com.paysi.identity.domain.Account;
import com.paysi.identity.domain.AccountStatus;
import com.paysi.identity.domain.KycStatus;
import com.paysi.identity.domain.PayoutDelay;
import com.paysi.identity.domain.PersonType;
import com.paysi.identity.domain.TaxId;
import com.paysi.identity.kyc.port.KycStore;
import com.paysi.identity.port.AccountRepository;
import com.paysi.payment.provider.SubaccountProvider;
import com.paysi.payment.provider.SubaccountProvider.SubaccountResult;
import com.paysi.payment.provider.WalletLookup;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AsaasKycProviderTest {
    private static final UUID ACCOUNT_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-09-29T12:00:00Z");

    private final AccountRepository accounts = mock(AccountRepository.class);
    private final KycStore store = mock(KycStore.class);
    private final WalletLookup wallets = mock(WalletLookup.class);
    private final SubaccountProvider subaccounts = mock(SubaccountProvider.class);
    private final AsaasKycProvider provider = new AsaasKycProvider(accounts, store, wallets, subaccounts,
            Clock.fixed(NOW, ZoneOffset.UTC));

    private static Account account() {
        return Account.reconstitute(ACCOUNT_ID, "ana@example.com", "hash", "Ana Vendedora", PersonType.PF,
                new TaxId("52998224725"), KycStatus.PENDING, PayoutDelay.D32, 0, AccountStatus.ACTIVE, NOW);
    }

    @Test
    void creatingTheSubaccountNeverApprovesTheAccountByItself() {
        when(wallets.walletId(ACCOUNT_ID)).thenReturn(Optional.empty());
        when(accounts.findById(ACCOUNT_ID)).thenReturn(Optional.of(account()));
        when(subaccounts.createSubaccount("Ana Vendedora", "ana@example.com", "52998224725"))
                .thenReturn(new SubaccountResult("acc_1", "wallet_1"));

        var process = provider.createProcess(ACCOUNT_ID);

        assertThat(process.providerProcessId()).isEqualTo("wallet_1");
        assertThat(process.providerUrl()).isNull();
        assertThat(process.requirements()).hasSize(2);
        assertThat(process.requirements().get(0).status()).isEqualTo("APPROVED");
        assertThat(process.requirements().get(1).status()).isEqualTo("PENDING");
        verify(store).attachProviderAccount(ACCOUNT_ID, "wallet_1");
    }

    @Test
    void anAccountThatAlreadyHasAWalletNeverCreatesAnother() {
        when(wallets.walletId(ACCOUNT_ID)).thenReturn(Optional.of("wallet_existing"));

        String walletId = provider.ensureSubaccount(ACCOUNT_ID);

        assertThat(walletId).isEqualTo("wallet_existing");
        verify(subaccounts, never()).createSubaccount(eq("Ana Vendedora"), eq("ana@example.com"), eq("52998224725"));
        verify(store, never()).attachProviderAccount(eq(ACCOUNT_ID), eq("wallet_existing"));
    }

    @Test
    void springInstantiatesTheBeanOnlyWhenAsaasIsTheKycProvider() {
        var runner = new org.springframework.boot.test.context.runner.ApplicationContextRunner()
                .withBean(AccountRepository.class, () -> mock(AccountRepository.class))
                .withBean(KycStore.class, () -> mock(KycStore.class))
                .withBean(WalletLookup.class, () -> mock(WalletLookup.class))
                .withBean(SubaccountProvider.class, () -> mock(SubaccountProvider.class))
                .withUserConfiguration(AsaasKycProvider.class, ConfiguredKycProvider.class);

        runner.withPropertyValues("paysi.kyc.provider=asaas").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(AsaasKycProvider.class).doesNotHaveBean(ConfiguredKycProvider.class);
        });
        runner.withPropertyValues("paysi.kyc.provider=configured").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(ConfiguredKycProvider.class).doesNotHaveBean(AsaasKycProvider.class);
        });
    }
}
