package com.paysi.identity.kyc.app;

import com.paysi.core.error.ForbiddenException;
import com.paysi.identity.domain.KycStatus;
import com.paysi.identity.kyc.port.KycProvider;
import com.paysi.identity.kyc.port.KycStore;
import com.paysi.identity.port.AccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.util.UUID;

@Service
public class KycService {
    private final AccountRepository accounts;
    private final KycStore store;
    private final KycProvider provider;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public KycService(AccountRepository accounts, KycStore store, KycProvider provider) {
        this(accounts, store, provider, Clock.systemUTC());
    }

    KycService(AccountRepository accounts, KycStore store, KycProvider provider, Clock clock) {
        this.accounts = accounts; this.store = store; this.provider = provider; this.clock = clock;
    }

    @Transactional(readOnly = true)
    public KycView current(UUID accountId) {
        var account = accounts.findById(accountId).orElseThrow(() -> unavailable());
        var process = store.findProcess(accountId);
        return new KycView(accountId, account.kycStatus(), process.map(value -> value.providerUrl()).orElse(null), store.requirements(accountId));
    }

    @Transactional
    public KycView start(UUID accountId) {
        store.lockAccount(accountId);
        var account = accounts.findById(accountId).orElseThrow(() -> unavailable());
        if (account.kycStatus() == KycStatus.APPROVED) return current(accountId);
        var process = store.findProcess(accountId).filter(existing -> existing.activeAt(clock.instant()))
                .orElseGet(() -> { var created = provider.createProcess(accountId); store.saveStarted(accountId, created); return created; });
        return new KycView(accountId, KycStatus.SUBMITTED, process.providerUrl(), process.requirements());
    }

    private static ForbiddenException unavailable() { return new ForbiddenException("ACCOUNT_UNAVAILABLE", "Conta indisponível"); }
}
