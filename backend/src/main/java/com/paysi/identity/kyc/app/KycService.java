package com.paysi.identity.kyc.app;

import com.paysi.core.error.ConflictException;
import com.paysi.core.error.ForbiddenException;
import com.paysi.core.error.ValidationException;
import com.paysi.identity.domain.KycStatus;
import com.paysi.identity.kyc.port.KycProvider;
import com.paysi.identity.kyc.port.KycStore;
import com.paysi.identity.port.AccountRepository;
import com.paysi.payment.provider.SubaccountProvider;
import com.paysi.payment.provider.SubaccountProvider.SubaccountCreationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;

@Service
public class KycService {
    /** Só formatos comuns de imagem/PDF de documento — evita subir um arquivo qualquer pro provedor. */
    private static final java.util.Set<String> ALLOWED_DOCUMENT_TYPES = java.util.Set.of(
            "image/png", "image/jpeg", "application/pdf");
    private static final long MAX_DOCUMENT_BYTES = 10L * 1024 * 1024;

    private final AccountRepository accounts;
    private final KycStore store;
    private final KycProvider provider;
    private final SubaccountProvider subaccounts;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public KycService(AccountRepository accounts, KycStore store, KycProvider provider, SubaccountProvider subaccounts) {
        this(accounts, store, provider, subaccounts, Clock.systemUTC());
    }

    KycService(AccountRepository accounts, KycStore store, KycProvider provider, SubaccountProvider subaccounts, Clock clock) {
        this.accounts = accounts; this.store = store; this.provider = provider; this.subaccounts = subaccounts; this.clock = clock;
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
     * não tem subconta com chave salva (ex.: perdida antes desta funcionalidade existir), devolve vazio
     * em vez de erro — o vendedor só vê "nenhuma pendência" até reiniciar a verificação.
     */
    @Transactional(readOnly = true)
    public List<PendingDocumentView> pendingDocuments(UUID accountId) {
        return store.decryptedAccessToken(accountId)
                .map(token -> subaccounts.pendingDocuments(token).stream()
                        .map(item -> new PendingDocumentView(item.id(), item.status(), item.type(), item.description(), item.externalUrl()))
                        .toList())
                .orElseGet(List::of);
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
            subaccounts.submitDocument(token, documentGroupId, file, filename == null ? "documento" : filename, contentType);
        } catch (SubaccountCreationException error) {
            throw new ConflictException("KYC_DOCUMENT_REJECTED", "Não foi possível enviar o documento: " + error.getMessage(), null);
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
