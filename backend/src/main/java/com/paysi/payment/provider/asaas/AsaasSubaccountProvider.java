package com.paysi.payment.provider.asaas;

import com.paysi.payment.provider.SubaccountProvider;
import com.paysi.payment.provider.asaas.dto.AsaasSubaccountRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/** Adaptador real da Asaas para {@link SubaccountProvider}: cria a subconta white-label (POST /v3/accounts). */
@Component
@ConditionalOnProperty(name = "paysi.provider", havingValue = "asaas")
public class AsaasSubaccountProvider implements SubaccountProvider {
    private final AsaasClient client;

    AsaasSubaccountProvider(AsaasClient client) {
        this.client = client;
    }

    @Override
    public SubaccountResult createSubaccount(String name, String email, String taxIdDigits, String postalCode, LocalDate birthDate, long incomeValueCents) {
        try {
            var response = client.createSubaccount(new AsaasSubaccountRequest(name, email, taxIdDigits, postalCode, birthDate,
                    AsaasMapper.toReais(incomeValueCents)));
            return new SubaccountResult(response.id(), response.walletId());
        } catch (AsaasApiException error) {
            throw new SubaccountCreationException(error.getMessage(), error);
        }
    }
}
