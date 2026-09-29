package com.paysi.identity.kyc.app;

import com.paysi.core.error.ForbiddenException;
import com.paysi.core.error.ValidationException;
import com.paysi.identity.domain.KycStatus;
import com.paysi.identity.kyc.port.KycProvider;
import com.paysi.identity.kyc.port.KycStore;
import com.paysi.identity.port.AccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
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
        // Com um provedor que aprova na hora (ex.: FakeKycProvider em staging), o status já pode
        // ter virado APPROVED durante o createProcess acima; relê em vez de assumir SUBMITTED.
        var refreshed = accounts.findById(accountId).orElseThrow(() -> unavailable());
        return new KycView(accountId, refreshed.kycStatus(), process.providerUrl(), process.requirements());
    }

    /**
     * Completa CEP e data de nascimento (exigidos pela Asaas para criar a subconta) e invalida o processo
     * guardado, para que a próxima chamada a {@link #start} tente de novo em vez de repetir a pendência.
     */
    @Transactional
    public KycView saveComplianceProfile(UUID accountId, String postalCode, String birthDateRaw) {
        store.lockAccount(accountId);
        accounts.findById(accountId).orElseThrow(() -> unavailable());
        store.saveComplianceProfile(accountId, normalizePostalCode(postalCode), parseBirthDate(birthDateRaw));
        store.clearProcess(accountId);
        return current(accountId);
    }

    private static String normalizePostalCode(String value) {
        String digits = value == null ? "" : value.replaceAll("\\D", "");
        if (digits.length() != 8) {
            throw new ValidationException("INVALID_POSTAL_CODE", "Informe um CEP válido, com 8 dígitos", "postalCode");
        }
        return digits;
    }

    private LocalDate parseBirthDate(String value) {
        LocalDate date;
        try {
            date = LocalDate.parse(value == null ? "" : value.strip());
        } catch (DateTimeParseException error) {
            throw new ValidationException("INVALID_BIRTH_DATE", "Informe uma data de nascimento válida", "birthDate");
        }
        if (!date.isBefore(LocalDate.now(clock))) {
            throw new ValidationException("INVALID_BIRTH_DATE", "Informe uma data de nascimento válida", "birthDate");
        }
        return date;
    }

    private static ForbiddenException unavailable() { return new ForbiddenException("ACCOUNT_UNAVAILABLE", "Conta indisponível"); }
}
