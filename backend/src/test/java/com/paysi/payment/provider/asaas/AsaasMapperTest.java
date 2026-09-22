package com.paysi.payment.provider.asaas;

import com.paysi.payment.provider.*;
import com.paysi.payment.provider.asaas.dto.AsaasPaymentResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AsaasMapperTest {
    private static final ProviderBuyer BUYER = new ProviderBuyer("Buyer", "buyer@example.com", "PF", "52998224725");

    @Test
    void singlePaymentUsesValueNotTotalValue() {
        var request = new ProviderPaymentRequest(UUID.randomUUID(), 10_000, ProviderPaymentMethod.PIX,
                1, null, BUYER, new ProviderSplit(8_000, 500, 1_500));
        var created = AsaasMapper.toCreateRequest(request, "cus_123");

        assertThat(created.billingType()).isEqualTo("PIX");
        assertThat(created.value()).isEqualByComparingTo("100.00");
        assertThat(created.totalValue()).isNull();
        assertThat(created.installmentCount()).isNull();
    }

    @Test
    void installmentCardUsesTotalValueAndInstallmentCount() {
        var request = new ProviderPaymentRequest(UUID.randomUUID(), 30_000, ProviderPaymentMethod.CARD,
                3, "tok_abc", BUYER, new ProviderSplit(24_000, 1_500, 4_500));
        var created = AsaasMapper.toCreateRequest(request, "cus_123");

        assertThat(created.billingType()).isEqualTo("CREDIT_CARD");
        assertThat(created.value()).isNull();
        assertThat(created.totalValue()).isEqualByComparingTo("300.00");
        assertThat(created.installmentCount()).isEqualTo(3);
        assertThat(created.creditCard().creditCardToken()).isEqualTo("tok_abc");
    }

    @Test
    void boletoSendsDueDaysAsOffsetFromToday() {
        var request = new ProviderPaymentRequest(UUID.randomUUID(), 5_000, ProviderPaymentMethod.BOLETO,
                1, null, BUYER, new ProviderSplit(4_000, 300, 700), 5);
        var created = AsaasMapper.toCreateRequest(request, "cus_123");

        assertThat(created.dueDate()).isEqualTo(java.time.LocalDate.now(java.time.ZoneOffset.UTC).plusDays(5));
    }

    @Test
    void statusMapping() {
        assertThat(AsaasMapper.toStatus("CONFIRMED")).isEqualTo(ProviderChargeStatus.APPROVED);
        assertThat(AsaasMapper.toStatus("RECEIVED")).isEqualTo(ProviderChargeStatus.APPROVED);
        assertThat(AsaasMapper.toStatus("PENDING")).isEqualTo(ProviderChargeStatus.PENDING);
        assertThat(AsaasMapper.toStatus("OVERDUE")).isEqualTo(ProviderChargeStatus.EXPIRED);
        assertThat(AsaasMapper.toStatus(null)).isEqualTo(ProviderChargeStatus.ERROR);
    }

    @Test
    void centsToReaisRoundTrip() {
        assertThat(AsaasMapper.toReais(12_345)).isEqualByComparingTo("123.45");
        assertThat(AsaasMapper.toCents(new BigDecimal("123.45"))).isEqualTo(12_345);
    }

    @Test
    void pendingCardChargeWithChallengeUrlIsExposedAsChallengeRequired() {
        var response = new AsaasPaymentResponse("pay_1", "PENDING", new BigDecimal("100.00"), null,
                null, null, null, LocalDate.now(), "https://asaas.com/3ds/challenge");
        var result = AsaasMapper.toChargeResult(response, ProviderPaymentMethod.CARD, 1, null);

        assertThat(result.status()).isEqualTo(ProviderChargeStatus.PENDING);
        assertThat(result.threeDs().status()).isEqualTo("CHALLENGE_REQUIRED");
        assertThat(result.threeDs().redirectUrl()).isEqualTo("https://asaas.com/3ds/challenge");
    }

    @Test
    void installmentChargeDoesNotFabricateReceivables() {
        var response = new AsaasPaymentResponse("pay_2", "CONFIRMED", new BigDecimal("300.00"), null,
                null, null, null, LocalDate.now(), null);
        var result = AsaasMapper.toChargeResult(response, ProviderPaymentMethod.CARD, 3, null);

        assertThat(result.receivables()).isEmpty();
    }

    @Test
    void errorResultIsAlwaysNonBlankAndCarriesRetryability() {
        var declined = new AsaasApiException("invalid_creditCard", "boom", false, null);
        var orderId = UUID.randomUUID();
        var result = AsaasMapper.errorResult(orderId, declined);

        assertThat(result.providerChargeId()).isNotBlank();
        assertThat(result.status()).isEqualTo(ProviderChargeStatus.DECLINED);
        assertThat(result.errorCode()).isEqualTo("invalid_creditCard");
        assertThat(result.retryable()).isFalse();

        var timeout = new AsaasApiException("PROVIDER_TIMEOUT", "boom", true, null);
        assertThat(AsaasMapper.errorResult(orderId, timeout).status()).isEqualTo(ProviderChargeStatus.ERROR);
        assertThat(AsaasMapper.errorResult(orderId, timeout).retryable()).isTrue();
    }
}
