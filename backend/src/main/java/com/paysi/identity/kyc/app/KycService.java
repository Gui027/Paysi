package com.paysi.identity.kyc.app;

import com.paysi.core.error.ConflictException;
import com.paysi.core.error.ForbiddenException;
import com.paysi.core.error.ValidationException;
import com.paysi.identity.domain.KycStatus;
import com.paysi.identity.kyc.port.KycProvider;
import com.paysi.identity.kyc.port.KycStore;
import com.paysi.identity.kyc.domain.KycProcess;
import com.paysi.identity.kyc.domain.KycRequirement;
import com.paysi.identity.port.AccountRepository;
import com.paysi.ledger.app.LedgerService;
import com.paysi.ledger.domain.Bucket;
import com.paysi.ledger.domain.Direction;
import com.paysi.ledger.domain.LedgerCommand;
import com.paysi.ledger.domain.LedgerEntry;
import com.paysi.ledger.domain.LedgerReference;
import com.paysi.ledger.domain.Origin;
import com.paysi.ledger.domain.ReferenceType;
import com.paysi.ledger.domain.TransactionType;
import com.paysi.payment.provider.SubaccountProvider;
import com.paysi.payment.provider.SubaccountProvider.SubaccountCreationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class KycService {
    /** Só formatos comuns de imagem/PDF de documento — evita subir um arquivo qualquer pro provedor. */
    private static final java.util.Set<String> ALLOWED_DOCUMENT_TYPES = java.util.Set.of(
            "image/png", "image/jpeg", "application/pdf");
    private static final long MAX_DOCUMENT_BYTES = 10L * 1024 * 1024;
    private static final long VERIFICATION_FEE_CENTS = 1200;
    private static final UUID PLATFORM_REVENUE = UUID.fromString("00000000-0000-0000-0000-0000000000c2");

    private final AccountRepository accounts;
    private final KycStore store;
    private final KycProvider provider;
    private final SubaccountProvider subaccounts;
    private final LedgerService ledger;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public KycService(AccountRepository accounts, KycStore store, KycProvider provider, SubaccountProvider subaccounts,
                      LedgerService ledger) {
        this(accounts, store, provider, subaccounts, ledger, Clock.systemUTC());
    }

    KycService(AccountRepository accounts, KycStore store, KycProvider provider, SubaccountProvider subaccounts,
               LedgerService ledger, Clock clock) {
        this.accounts = accounts; this.store = store; this.provider = provider; this.subaccounts = subaccounts;
        this.ledger = ledger; this.clock = clock;
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
     * Documentos de verificação pendentes da subconta (ex.: documento de identidade, selfie/prova de
     * vida) — sempre consultados com a chave própria da subconta, nunca a da Paysi. Quando a conta ainda
     * não tem subconta com chave salva (ex.: perdida antes desta funcionalidade existir), devolve um erro
     * explícito. Lista vazia fica reservada para o caso real de não haver documentos pendentes.
     */
    @Transactional(readOnly = true)
    public List<PendingDocumentView> pendingDocuments(UUID accountId) {
        String token = store.decryptedAccessToken(accountId)
                .orElseThrow(() -> new ConflictException("KYC_CREDENTIAL_UNAVAILABLE",
                        "A conexão da conta de recebimento precisa ser restabelecida.", null));
        return pendingDocuments(token);
    }

    /**
     * Repara contas criadas antes de a Paysi persistir a chave da subconta. Nunca cria outra conta:
     * localiza a subconta existente pelo walletId, gera uma chave substituta e a armazena criptografada.
     */
    @Transactional
    public List<PendingDocumentView> reconnect(UUID accountId) {
        store.lockAccount(accountId);
        accounts.findById(accountId).orElseThrow(() -> unavailable());
        Optional<String> currentToken = store.decryptedAccessToken(accountId);
        if (currentToken.isPresent()) return pendingDocuments(currentToken.get());

        String walletId = store.providerAccountId(accountId)
                .orElseThrow(() -> new ConflictException("KYC_SUBACCOUNT_UNAVAILABLE",
                        "A conta de recebimento ainda não foi criada. Reinicie a verificação.", null));
        final String recoveredToken;
        try {
            recoveredToken = subaccounts.recoverAccessToken(walletId);
        } catch (SubaccountCreationException error) {
            throw new ConflictException("KYC_RECONNECT_UNAVAILABLE",
                    "Não foi possível restabelecer a conexão agora. O suporte da Paysi precisa liberar a reconexão na Asaas.", null);
        }
        store.saveProviderAccessToken(accountId, recoveredToken);
        return pendingDocuments(recoveredToken);
    }

    /**
     * Consulta pontualmente a situação cadastral da subconta. É o fallback explícito recomendado pela
     * Asaas quando a interface precisa refletir o resultado antes do webhook. A cobrança da verificação
     * usa a mesma chave natural do webhook e, portanto, continua idempotente se ambos chegarem juntos.
     */
    @Transactional
    public KycView refreshStatus(UUID accountId) {
        store.lockAccount(accountId);
        var account = accounts.findById(accountId).orElseThrow(() -> unavailable());
        if (account.kycStatus() == KycStatus.APPROVED) return current(accountId);
        String token = store.decryptedAccessToken(accountId)
                .orElseThrow(() -> new ConflictException("KYC_CREDENTIAL_UNAVAILABLE",
                        "A conexão da conta de recebimento precisa ser revisada. Fale com o suporte da Paysi.", null));
        final SubaccountProvider.SubaccountStatus providerStatus;
        try {
            providerStatus = subaccounts.accountStatus(token);
        } catch (SubaccountCreationException error) {
            throw new ConflictException("KYC_STATUS_UNAVAILABLE",
                    "Não foi possível consultar a análise agora. Tente novamente em instantes.", null);
        }

        KycStatus next = providerStatus.approved() ? KycStatus.APPROVED
                : providerStatus.rejected() ? KycStatus.REJECTED : KycStatus.SUBMITTED;
        List<KycRequirement> requirements = next == KycStatus.APPROVED ? List.of()
                : List.of(new KycRequirement("ASAAS_VERIFICATION", "Verificação de identidade na Asaas",
                next == KycStatus.REJECTED ? "REJECTED" : "PENDING",
                next == KycStatus.REJECTED
                        ? "A Asaas recusou algum dado ou documento. Revise as pendências e envie novamente."
                        : "A análise da Asaas ainda está em andamento.", null));
        store.updateStatus(accountId, next, requirements);

        if (next == KycStatus.APPROVED) {
            ledger.write(new LedgerCommand(TransactionType.PLATFORM_FEE,
                    new LedgerReference(ReferenceType.VERIFICATION, accountId.toString()), "Taxa de verificação KYC",
                    List.of(new LedgerEntry(accountId, Bucket.DEBT, Direction.DEBIT, VERIFICATION_FEE_CENTS, Origin.FEE, null),
                            new LedgerEntry(PLATFORM_REVENUE, Bucket.SYSTEM, Direction.CREDIT, VERIFICATION_FEE_CENTS, Origin.FEE, null))));
        }
        String providerUrl = store.findProcess(accountId).map(KycProcess::providerUrl).orElse(null);
        return new KycView(accountId, next, providerUrl, requirements);
    }

    /**
     * Envia um documento para o provedor, em nome da subconta do próprio vendedor — nada sai da Paysi.
     * {@code documentGroupId} precisa ser um dos ids devolvidos por {@link #pendingDocuments}.
     */
    @Transactional
    public void submitDocument(UUID accountId, String documentGroupId, byte[] file, String filename, String contentType) {
        String token = store.decryptedAccessToken(accountId)
                .orElseThrow(() -> new ConflictException("KYC_NO_SUBACCOUNT", "Inicie a verificação antes de enviar documentos", null));
        if (file == null || file.length == 0 || file.length > MAX_DOCUMENT_BYTES) {
            throw new ValidationException("INVALID_DOCUMENT_FILE", "O arquivo deve ter até 10 MB", "file");
        }
        if (contentType == null || !ALLOWED_DOCUMENT_TYPES.contains(contentType)) {
            throw new ValidationException("INVALID_DOCUMENT_TYPE", "Envie uma imagem (PNG/JPEG) ou PDF", "file");
        }
        if (documentGroupId == null || documentGroupId.isBlank()) {
            throw new ValidationException("INVALID_DOCUMENT_ID", "Documento inválido", "documentGroupId");
        }
        try {
            var requested = subaccounts.pendingDocuments(token).stream()
                    .filter(document -> documentGroupId.equals(document.id()))
                    .findFirst()
                    .orElseThrow(() -> new ValidationException("INVALID_DOCUMENT_ID", "Esta pendência não está mais disponível. Atualize a verificação.", "documentGroupId"));
            if (requested.externalUrl() != null || requiresExternalOnboarding(requested.description())) {
                throw new ConflictException("KYC_DOCUMENT_EXTERNAL", "Este documento deve ser enviado pelo link seguro de verificação da Asaas.", null);
            }
            subaccounts.submitDocument(token, documentGroupId, requested.type(), file,
                    filename == null ? "documento" : filename, contentType);
        } catch (SubaccountCreationException error) {
            throw new ConflictException("KYC_DOCUMENT_REJECTED", "Não foi possível enviar o documento: " + error.getMessage(), null);
        }
    }

    private static boolean requiresExternalOnboarding(String description) {
        if (description == null) return false;
        String normalized = description.toLowerCase(java.util.Locale.ROOT);
        return normalized.contains("link de onboarding") || normalized.contains("aplicativo");
    }

    private List<PendingDocumentView> pendingDocuments(String token) {
        try {
            return subaccounts.pendingDocuments(token).stream()
                    .map(item -> new PendingDocumentView(item.id(), item.status(), item.type(), item.description(), item.externalUrl()))
                    .toList();
        } catch (SubaccountCreationException error) {
            throw new ConflictException("KYC_DOCUMENTS_UNAVAILABLE",
                    "Não foi possível consultar os documentos na Asaas agora. Tente novamente em instantes.", null);
        }
    }

    /**
     * Completa CEP, data de nascimento e renda/faturamento (exigidos pela Asaas para criar a subconta) e
     * invalida o processo guardado, para que a próxima chamada a {@link #start} tente de novo em vez de
     * repetir a pendência. {@code incomeValueCents} já vem calculado do painel (o mesmo padrão de
     * {@code parseMoneyToCents} usado no resto do sistema) — aqui só se valida que não é negativo.
     */
    @Transactional
    public KycView saveComplianceProfile(UUID accountId, String postalCode, String birthDateRaw, Long incomeValueCents) {
        store.lockAccount(accountId);
        accounts.findById(accountId).orElseThrow(() -> unavailable());
        if (incomeValueCents == null || incomeValueCents < 0) {
            throw new ValidationException("INVALID_INCOME_VALUE", "Informe uma renda/faturamento válido", "incomeValueCents");
        }
        store.saveComplianceProfile(accountId, normalizePostalCode(postalCode), parseBirthDate(birthDateRaw), incomeValueCents);
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

    public record PendingDocumentView(String id, String status, String type, String description, String externalUrl) { }
}
