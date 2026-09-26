package com.paysi.finance.app;

import com.paysi.core.error.ConflictException;
import com.paysi.core.error.ForbiddenException;
import com.paysi.core.error.NotFoundException;
import com.paysi.core.error.ValidationException;
import com.paysi.finance.app.FinanceModels.AccountRow;
import com.paysi.finance.app.FinanceModels.Balance;
import com.paysi.finance.app.FinanceModels.Fees;
import com.paysi.finance.app.FinanceModels.Holder;
import com.paysi.finance.app.FinanceModels.MethodFee;
import com.paysi.finance.app.FinanceModels.Overview;
import com.paysi.finance.app.FinanceModels.PayoutRaw;
import com.paysi.finance.app.FinanceModels.PayoutRow;
import com.paysi.finance.app.FinanceModels.PayoutsPage;
import com.paysi.finance.app.FinanceModels.PixAccount;
import com.paysi.finance.port.FinanceRepository;
import com.paysi.identity.domain.PersonType;
import com.paysi.identity.domain.TaxId;
import com.paysi.identity.port.PlatformPlanReader;
import com.paysi.ledger.jobs.app.LedgerReleaseProcessor;
import com.paysi.ledger.query.app.BalanceView;
import com.paysi.ledger.query.app.LedgerQueryService;
import com.paysi.payment.split.PaymentMethod;
import com.paysi.payment.split.Plan;
import com.paysi.payment.split.SplitEngine;
import com.paysi.payout.domain.BankAccount;
import com.paysi.payout.port.PayoutRepository;
import com.paysi.security.mfa.app.MfaGuard;
import com.paysi.security.mfa.domain.MfaCredential;
import com.paysi.security.mfa.domain.SensitiveOperation;
import com.paysi.security.mfa.port.MfaStore;
import com.paysi.security.mfa.port.SecretProtector;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

@Service
public class FinanceService {
    public static final long MIN_PAYOUT_CENTS = 200;
    public static final long PAYOUT_FEE_CENTS = 0;
    public static final int RESERVE_DAYS = 90;
    public static final int DEFAULT_PAGE_SIZE = 10;
    public static final int MAX_PAGE_SIZE = 50;
    private static final String PIX_ACCOUNT_PLACEHOLDER = "00000";

    private final FinanceRepository repository;
    private final PayoutRepository payouts;
    private final SecretProtector protector;
    private final MfaGuard mfa;
    private final MfaStore mfaStore;
    private final LedgerQueryService ledger;
    private final PlatformPlanReader plans;
    private final long mfaThresholdCents;
    private final Clock clock;

    @Autowired
    public FinanceService(FinanceRepository repository, PayoutRepository payouts, SecretProtector protector,
                          MfaGuard mfa, MfaStore mfaStore, LedgerQueryService ledger, PlatformPlanReader plans,
                          @Value("${paysi.payout.mfa-threshold-cents:100000}") long mfaThresholdCents) {
        this(repository, payouts, protector, mfa, mfaStore, ledger, plans, mfaThresholdCents, Clock.systemUTC());
    }

    FinanceService(FinanceRepository repository, PayoutRepository payouts, SecretProtector protector, MfaGuard mfa,
                   MfaStore mfaStore, LedgerQueryService ledger, PlatformPlanReader plans, long mfaThresholdCents,
                   Clock clock) {
        this.repository = repository;
        this.payouts = payouts;
        this.protector = protector;
        this.mfa = mfa;
        this.mfaStore = mfaStore;
        this.ledger = ledger;
        this.plans = plans;
        this.mfaThresholdCents = mfaThresholdCents;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Overview overview(UUID accountId) {
        AccountRow account = requireAccount(accountId);
        BalanceView balance = ledger.balance(accountId);
        PixAccount pix = repository.activeBank(accountId)
                .map(bank -> new PixAccount(bank.id(), bank.pixKeyType(), decrypt(bank.pixKeyEncrypted()), bank.verifiedAt()))
                .orElse(null);
        return new Overview(new Holder(account.fullName(), account.personType(), account.taxId(), "BR"),
                new Balance(balance.available(), Math.addExact(balance.guarantee(), balance.pending()),
                        balance.reserve(), balance.debt()),
                pix, account.kycStatus(), mfaEnabled(accountId), PAYOUT_FEE_CENTS, MIN_PAYOUT_CENTS,
                mfaThresholdCents);
    }

    @Transactional(readOnly = true)
    public PayoutsPage payoutsPage(UUID accountId, Integer requestedPage, Integer requestedSize) {
        int size = requestedSize == null ? DEFAULT_PAGE_SIZE : Math.max(1, Math.min(requestedSize, MAX_PAGE_SIZE));
        long total = repository.countPayouts(accountId);
        int totalPages = (int) Math.max(1, (total + size - 1) / size);
        int page = Math.max(1, Math.min(requestedPage == null ? 1 : requestedPage, totalPages));
        List<PayoutRow> rows = repository.payouts(accountId, size, (page - 1) * size).stream().map(this::row).toList();
        return new PayoutsPage(rows, page, size, total, totalPages);
    }

    @Transactional(readOnly = true)
    public Fees fees(UUID accountId) {
        AccountRow account = requireAccount(accountId);
        Plan plan = currentPlan(accountId);
        List<MethodFee> methods = java.util.Arrays.stream(PaymentMethod.values())
                .map(method -> new MethodFee(method.name(), method.feeBps(plan), SplitEngine.PLATFORM_FIXED_FEE_CENTS))
                .toList();
        return new Fees(plan.name(), methods, days(account.payoutDelay()),
                LedgerReleaseProcessor.reserveBps(account.payoutDelay()), RESERVE_DAYS);
    }

    /** Cadastra ou troca a chave Pix de recebimento. Trocar uma chave existente exige o segundo fator. */
    @Transactional
    public PixAccount savePixKey(UUID accountId, String rawKey, UUID challengeId) {
        AccountRow account = requireAccount(accountId);
        var detected = PixKeys.detect(rawKey);
        requireKeyOfHolder(detected, account.taxId());
        List<UUID> existing = repository.activeBankIds(accountId);
        if (!existing.isEmpty()) requireMfa(accountId, challengeId);
        return replaceBank(accountId, account.fullName(), account.personType(), account.taxId(), detected, existing);
    }

    /** Passa a conta de CPF para CNPJ. Irreversível; a nova empresa precisa passar pela verificação de identidade. */
    @Transactional
    public PixAccount convertToCompany(UUID accountId, String legalName, String rawCnpj, String rawPixKey,
                                       UUID challengeId) {
        AccountRow account = requireAccount(accountId);
        if (!"PF".equals(account.personType())) {
            throw new ConflictException("ACCOUNT_CONVERSION_UNAVAILABLE", "Esta conta já é de pessoa jurídica", null);
        }
        String name = legalName == null ? "" : legalName.strip();
        if (name.length() < 3 || name.length() > 120) {
            throw new ValidationException("LEGAL_NAME_INVALID", "Informe a razão social (3 a 120 caracteres)", "legalName");
        }
        String cnpj = TaxId.of(rawCnpj, PersonType.PJ).digits();
        var detected = PixKeys.detect(rawPixKey);
        requireKeyOfHolder(detected, cnpj);
        requireMfa(accountId, challengeId);

        List<UUID> existing = repository.activeBankIds(accountId);
        repository.convertToCompany(accountId, name, cnpj, account.personType(), account.taxId());
        return replaceBank(accountId, name, "PJ", cnpj, detected, existing);
    }

    private PixAccount replaceBank(UUID accountId, String holderName, String personType, String taxId,
                                   PixKeys.Detected key, List<UUID> existing) {
        var now = clock.instant();
        var bank = new BankAccount(UUID.randomUUID(), accountId, "PIX", "-", "0000", taxId, personType, holderName,
                "PAYMENT", key.type(), now, null);
        payouts.insertBank(bank, protector.encrypt(PIX_ACCOUNT_PLACEHOLDER.getBytes(StandardCharsets.UTF_8)),
                protector.encrypt(key.key().getBytes(StandardCharsets.UTF_8)));
        existing.forEach(id -> payouts.archiveBank(accountId, id, now));
        return new PixAccount(bank.id(), key.type(), key.key(), now);
    }

    /** Chave que é o próprio CPF/CNPJ só pode ser do titular; e-mail, celular e chave aleatória não dá para conferir. */
    private static void requireKeyOfHolder(PixKeys.Detected key, String holderTaxId) {
        if (("CPF".equals(key.type()) || "CNPJ".equals(key.type())) && !key.key().equals(holderTaxId)) {
            throw new ValidationException("BANK_HOLDER_MISMATCH",
                    "A chave Pix precisa pertencer ao mesmo titular da conta", "pixKey");
        }
    }

    private void requireMfa(UUID accountId, UUID challengeId) {
        if (!mfaEnabled(accountId)) {
            throw new ForbiddenException("MFA_REQUIRED", "Ative a verificação em duas etapas para continuar");
        }
        if (challengeId == null) {
            throw new ForbiddenException("MFA_CHALLENGE_INVALID", "Confirme o código de segurança para continuar");
        }
        mfa.consume(accountId, challengeId, SensitiveOperation.BANK_ACCOUNT_CHANGE);
    }

    private boolean mfaEnabled(UUID accountId) {
        return mfaStore.credential(accountId).filter(MfaCredential::enabled).isPresent();
    }

    private AccountRow requireAccount(UUID accountId) {
        return repository.account(accountId).orElseThrow(
                () -> new NotFoundException("ACCOUNT_NOT_FOUND", "Conta não encontrada"));
    }

    private Plan currentPlan(UUID accountId) {
        try {
            return Plan.valueOf(plans.currentPlan(accountId));
        } catch (RuntimeException error) {
            return Plan.TRANSACIONAL;
        }
    }

    private PayoutRow row(PayoutRaw raw) {
        return new PayoutRow(raw.id(), raw.createdAt(), raw.amountCents(), raw.status(), raw.holderName(),
                raw.pixKeyType(), decrypt(raw.pixKeyEncrypted()), raw.receiptUrl());
    }

    private String decrypt(byte[] encrypted) {
        return encrypted == null ? null : new String(protector.decrypt(encrypted), StandardCharsets.UTF_8);
    }

    private static int days(String payoutDelay) {
        return Integer.parseInt(payoutDelay.replaceAll("\\D", ""));
    }
}
