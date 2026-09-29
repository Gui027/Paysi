package com.paysi.payment.provider.asaas;

import com.paysi.payment.provider.SubaccountProvider.SubaccountCreationException;
import com.paysi.payment.provider.asaas.dto.AsaasAccountResponse;
import com.paysi.payment.provider.asaas.dto.AsaasSubaccountRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AsaasSubaccountProviderTest {
    @Test
    void returnsTheAccountIdAndTheWalletIdFromTheAsaasResponse() {
        AsaasClient client = mock(AsaasClient.class);
        when(client.createSubaccount(new AsaasSubaccountRequest("Ana Vendedora", "ana@example.com", "52998224725")))
                .thenReturn(new AsaasAccountResponse("acc_123", "wallet_abc"));
        var provider = new AsaasSubaccountProvider(client);

        var result = provider.createSubaccount("Ana Vendedora", "ana@example.com", "52998224725");

        assertThat(result.accountId()).isEqualTo("acc_123");
        assertThat(result.walletId()).isEqualTo("wallet_abc");
    }

    @Test
    void wrapsAnAsaasFailureAsASubaccountCreationExceptionWithoutInventingData() {
        AsaasClient client = mock(AsaasClient.class);
        when(client.createSubaccount(any())).thenThrow(new AsaasApiException("INVALID_MOBILE_PHONE",
                "Asaas respondeu 400: mobilePhone é obrigatório", false, null));
        var provider = new AsaasSubaccountProvider(client);

        assertThatThrownBy(() -> provider.createSubaccount("Ana", "ana@example.com", "52998224725"))
                .isInstanceOf(SubaccountCreationException.class)
                .hasMessageContaining("mobilePhone");
    }
}
