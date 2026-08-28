package com.paysi.payment.provider;

import com.paysi.core.error.ConflictException;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.*;

abstract class PaymentProviderContractTest {
    protected abstract PaymentProvider provider(FakeProviderOutcome outcome);

    @Test
    void reproducesEveryConfiguredBusinessState() {
        assertThat(provider(FakeProviderOutcome.APPROVED).charge(request()).status())
                .isEqualTo(ProviderChargeStatus.APPROVED);
        assertThat(provider(FakeProviderOutcome.DECLINED).charge(request()).status())
                .isEqualTo(ProviderChargeStatus.DECLINED);
        assertThat(provider(FakeProviderOutcome.PENDING).charge(request()).status())
                .isEqualTo(ProviderChargeStatus.PENDING);
        assertThat(provider(FakeProviderOutcome.EXPIRED).charge(request()).status())
                .isEqualTo(ProviderChargeStatus.EXPIRED);
        assertThat(provider(FakeProviderOutcome.ERROR).charge(request()).status())
                .isEqualTo(ProviderChargeStatus.ERROR);
    }

    @Test
    void timeoutMapsToRetryableErrorAndNeverToApproval() {
        var result = provider(FakeProviderOutcome.TIMEOUT).charge(request());
        assertThat(result.status()).isEqualTo(ProviderChargeStatus.ERROR);
        assertThat(result.errorCode()).isEqualTo("PROVIDER_TIMEOUT");
        assertThat(result.retryable()).isTrue();
        assertThat(result.receivables()).isEmpty();
    }

    @Test
    void sameOrderIsIdempotentUnderConcurrencyAndChangedBodyConflicts() throws Exception {
        var provider = provider(FakeProviderOutcome.APPROVED);
        var request = request();
        try (var executor = Executors.newFixedThreadPool(8)) {
            var tasks = java.util.stream.IntStream.range(0, 40)
                    .mapToObj(index -> (Callable<ProviderPaymentResult>) () -> provider.charge(request))
                    .toList();
            var results = executor.invokeAll(tasks).stream().map(future -> {
                try {
                    return future.get();
                } catch (Exception exception) {
                    throw new RuntimeException(exception);
                }
            }).toList();
            assertThat(results).allMatch(results.getFirst()::equals);
        }

        var changed = new ProviderPaymentRequest(request.orderId(), 10_001,
                ProviderPaymentMethod.CARD, 3, "another-token", request.buyer(),
                new ProviderSplit(8_001, 500, 1_500));
        assertThatThrownBy(() -> provider.charge(changed)).isInstanceOf(ConflictException.class);
    }

    @Test
    void normalizedContractContainsTokenButNeverPanOrCvv() {
        Set<String> forbidden = Set.of("pan", "cvv", "cardnumber", "securitycode");
        var names = Arrays.stream(ProviderPaymentRequest.class.getRecordComponents())
                .map(RecordComponent::getName).map(String::toLowerCase).toList();
        assertThat(names).contains("paymenttoken");
        assertThat(names).doesNotContainAnyElementsOf(forbidden);
        assertThat(request().toString()).contains("paymentToken=[REDACTED]", "buyer=[REDACTED]")
                .doesNotContain("tok_test_123", "buyer@example.com", "52998224725");
    }

    @Test
    void validatesNormalizedAmountSplitMethodAndToken() {
        var buyer = new ProviderBuyer("Buyer", "buyer@example.com", "PF", "52998224725");
        assertThatThrownBy(() -> new ProviderPaymentRequest(UUID.randomUUID(), 1_000,
                ProviderPaymentMethod.CARD, 2, null, buyer, new ProviderSplit(800, 0, 200)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ProviderPaymentRequest(UUID.randomUUID(), 1_000,
                ProviderPaymentMethod.PIX, 2, null, buyer, new ProviderSplit(800, 0, 200)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ProviderPaymentRequest(UUID.randomUUID(), 1_000,
                ProviderPaymentMethod.PIX, 1, null, buyer, new ProviderSplit(799, 0, 200)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    protected static ProviderPaymentRequest request() {
        return new ProviderPaymentRequest(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                10_000,
                ProviderPaymentMethod.CARD,
                3,
                "tok_test_123",
                new ProviderBuyer("Buyer", "buyer@example.com", "PF", "52998224725"),
                new ProviderSplit(8_000, 500, 1_500));
    }
}
