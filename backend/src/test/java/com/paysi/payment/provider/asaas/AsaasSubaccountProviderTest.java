package com.paysi.payment.provider.asaas;

import com.paysi.payment.provider.SubaccountProvider.SubaccountCreationException;
import com.paysi.payment.provider.asaas.dto.AsaasAccountResponse;
import com.paysi.payment.provider.asaas.dto.AsaasSubaccountRequest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AsaasSubaccountProviderTest {
    private static final LocalDate BIRTH_DATE = LocalDate.of(1990, 5, 20);

    @Test
    void returnsTheAccountIdAndTheWalletIdFromTheAsaasResponseConvertingIncomeCentsToReais() {
        AsaasClient client = mock(AsaasClient.class);
        when(client.createSubaccount(new AsaasSubaccountRequest("Ana Vendedora", "ana@example.com", "52998224725", "01310100", BIRTH_DATE, new BigDecimal("1500.00"))))
                .thenReturn(new AsaasAccountResponse("acc_123", "wallet_abc"));
        var provider = new AsaasSubaccountProvider(client);

        var result = provider.createSubaccount("Ana Vendedora", "ana@example.com", "52998224725", "01310100", BIRTH_DATE, 150_000);

        assertThat(result.accountId()).isEqualTo("acc_123");
        assertThat(result.walletId()).isEqualTo("wallet_abc");
    }

    @Test
    void wrapsAnAsaasFailureAsASubaccountCreationExceptionWithoutInventingData() {
        AsaasClient client = mock(AsaasClient.class);
        when(client.createSubaccount(any())).thenThrow(new AsaasApiException("INVALID_OBJECT",
                "Asaas respondeu 400: É necessário informar a renda/faturamento.", false, null));
        var provider = new AsaasSubaccountProvider(client);

        assertThatThrownBy(() -> provider.createSubaccount("Ana", "ana@example.com", "52998224725", "01310100", BIRTH_DATE, 150_000))
                .isInstanceOf(SubaccountCreationException.class)
                .hasMessageContaining("renda");
    }
}
