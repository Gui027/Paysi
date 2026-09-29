package com.paysi.identity.kyc.adapter;

import com.paysi.identity.domain.Account;
import com.paysi.identity.kyc.domain.KycProcess;
import com.paysi.identity.kyc.domain.KycRequirement;
import com.paysi.identity.kyc.port.KycProvider;
import com.paysi.identity.kyc.port.KycStore;
import com.paysi.identity.port.AccountRepository;
import com.paysi.payment.provider.SubaccountProvider;
import com.paysi.payment.provider.WalletLookup;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Verificação de identidade real via subconta da Asaas ({@code paysi.kyc.provider=asaas}).
 *
 * <p>Criar a subconta NÃO aprova a conta — a Paysi só sabe que a subconta foi criada, não que ela foi
 * verificada. A verificação de documento/prova de vida acontece do lado da Asaas; até existir um jeito de
 * a Asaas avisar a Paysi disso (webhook de "Situação da conta", ainda não modelado aqui), a conta fica em
 * {@code SUBMITTED} — nunca aprovada sozinha. É de propósito: melhor mostrar "em análise" de verdade do
 * que aprovar sem checar nada (era o que o {@code FakeKycProvider} fazia).</p>
 *
 * <p>Só pede nome, e-mail e documento à Asaas (ver {@link SubaccountProvider}): se a Asaas exigir mais
 * campos (telefone, endereço, tipo de empresa para CNPJ), a criação falha com um erro claro em vez da
 * Paysi inventar um valor — o chamador (KycService) deixa esse erro subir.</p>
 */
@Component
@ConditionalOnProperty(name = "paysi.kyc.provider", havingValue = "asaas")
public class AsaasKycProvider implements KycProvider {
    private static final Duration REQUIREMENT_HORIZON = Duration.ofDays(365);

    private final AccountRepository accounts;
    private final KycStore store;
    private final WalletLookup wallets;
    private final SubaccountProvider subaccounts;
    private final Clock clock;

    @Autowired
    public AsaasKycProvider(AccountRepository accounts, KycStore store, WalletLookup wallets, SubaccountProvider subaccounts) {
        this(accounts, store, wallets, subaccounts, Clock.systemUTC());
    }

    AsaasKycProvider(AccountRepository accounts, KycStore store, WalletLookup wallets, SubaccountProvider subaccounts, Clock clock) {
        this.accounts = accounts;
        this.store = store;
        this.wallets = wallets;
        this.subaccounts = subaccounts;
        this.clock = clock;
    }

    @Override
    public KycProcess createProcess(UUID accountId) {
        String walletId = ensureSubaccount(accountId);
        List<KycRequirement> requirements = List.of(
                new KycRequirement("ASAAS_SUBACCOUNT", "Conta na Asaas", "APPROVED", null, null),
                new KycRequirement("ASAAS_VERIFICATION", "Verificação de identidade na Asaas", "PENDING",
                        "Conclua a verificação de documento e a prova de vida diretamente no painel da Asaas.", null));
        return new KycProcess(walletId, null, clock.instant().plus(REQUIREMENT_HORIZON), requirements);
    }

    @Override
    public String ensureSubaccount(UUID accountId) {
        return wallets.walletId(accountId).orElseGet(() -> {
            Account account = accounts.findById(accountId)
                    .orElseThrow(() -> new IllegalStateException("Conta não encontrada para criar subconta na Asaas"));
            var created = subaccounts.createSubaccount(account.fullName(), account.email(), account.taxId().digits());
            store.attachProviderAccount(accountId, created.walletId());
            return created.walletId();
        });
    }
}
