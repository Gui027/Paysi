package com.paysi.payment.provider.asaas;

import com.paysi.payment.provider.SubaccountProvider;
import com.paysi.payment.provider.asaas.dto.AsaasSubaccountRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * Adaptador real da Asaas para {@link SubaccountProvider}: cria a subconta white-label (POST /v3/accounts)
 * e gerencia os documentos de verificação dela, sempre com a chave própria da subconta.
 */
@Component
@ConditionalOnProperty(name = "paysi.provider", havingValue = "asaas")
public class AsaasSubaccountProvider implements SubaccountProvider {
    private final AsaasClient client;
    private final AsaasSubaccountDocumentsClient documents;

    AsaasSubaccountProvider(AsaasClient client, AsaasSubaccountDocumentsClient documents) {
        this.client = client;
        this.documents = documents;
    }

    @Override
    public SubaccountResult createSubaccount(String name, String email, String taxIdDigits, String postalCode, LocalDate birthDate, long incomeValueCents) {
        try {
            var response = client.createSubaccount(new AsaasSubaccountRequest(name, email, taxIdDigits, postalCode, birthDate,
                    AsaasMapper.toReais(incomeValueCents)));
            return new SubaccountResult(response.id(), response.walletId(), response.apiKey());
        } catch (AsaasApiException error) {
            throw new SubaccountCreationException(error.getMessage(), error);
        }
    }

    @Override
    public String recoverAccessToken(String walletId) {
        try {
            var account = client.findSubaccountByWalletId(walletId);
            var created = client.createSubaccountAccessToken(account.id());
            if (created == null || created.apiKey() == null || created.apiKey().isBlank()) {
                throw new SubaccountCreationException("A Asaas não devolveu a nova chave da subconta", null);
            }
            return created.apiKey();
        } catch (AsaasApiException error) {
            throw new SubaccountCreationException(error.getMessage(), error);
        }
    }

    @Override
    public List<PendingDocument> pendingDocuments(String subaccountApiKey) {
        try {
            return documents.listPendingDocuments(subaccountApiKey).stream()
                    .map(group -> new PendingDocument(group.id(), group.status(), group.type(), group.description(), group.onboardingUrl()))
                    .toList();
        } catch (AsaasApiException error) {
            throw new SubaccountCreationException(error.getMessage(), error);
        }
    }

    @Override
    public SubaccountStatus accountStatus(String subaccountApiKey) {
        try {
            var status = documents.accountStatus(subaccountApiKey);
            if (status == null) throw new SubaccountCreationException("A Asaas não retornou a situação da conta", null);
            return new SubaccountStatus(status.general(), status.commercialInfo(), status.bankAccountInfo(), status.documentation());
        } catch (AsaasApiException error) {
            throw new SubaccountCreationException(error.getMessage(), error);
        }
    }

    @Override
    public void submitDocument(String subaccountApiKey, String documentGroupId, String documentType, byte[] file, String filename, String contentType) {
        try {
            documents.submitDocument(subaccountApiKey, documentGroupId, documentType, file, filename, contentType);
        } catch (AsaasApiException error) {
            throw new SubaccountCreationException(error.getMessage(), error);
        }
    }
}
