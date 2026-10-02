package com.paysi.payment.provider;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Substitui {@link SubaccountProvider} em dev/teste ({@code paysi.provider=fake}): não fala com nada de fora. */
@Component
@ConditionalOnProperty(name = "paysi.provider", havingValue = "fake")
public class FakeSubaccountProvider implements SubaccountProvider {
    @Override
    public SubaccountResult createSubaccount(String name, String email, String taxIdDigits, String postalCode, LocalDate birthDate, long incomeValueCents) {
        String id = "fake_acc_" + UUID.randomUUID();
        return new SubaccountResult(id, "fake_wallet_" + UUID.randomUUID(), "fake_key_" + UUID.randomUUID());
    }

    @Override
    public String recoverAccessToken(String walletId) {
        return "fake_key_recovered_" + walletId;
    }

    @Override
    public List<PendingDocument> pendingDocuments(String subaccountApiKey) {
        return List.of();
    }

    @Override
    public SubaccountStatus accountStatus(String subaccountApiKey) {
        return new SubaccountStatus("APPROVED", "APPROVED", "APPROVED", "APPROVED");
    }

    @Override
    public void submitDocument(String subaccountApiKey, String documentGroupId, String documentType, byte[] file, String filename, String contentType) {
        // sem-op: nada de verdade pra enviar em dev/teste
    }
}
