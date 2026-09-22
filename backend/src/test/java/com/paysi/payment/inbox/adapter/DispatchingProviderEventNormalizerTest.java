package com.paysi.payment.inbox.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DispatchingProviderEventNormalizerTest {
    private final DispatchingProviderEventNormalizer normalizer =
            new DispatchingProviderEventNormalizer(new ObjectMapper().findAndRegisterModules());

    @Test
    void fakeProviderStaysOnTheAlreadyFlatFormat() throws Exception {
        String raw = """
                {"providerEventId":"evt-1","eventType":"PAYMENT_CONFIRMED",
                 "chargeId":"11111111-1111-1111-1111-111111111111",
                 "providerChargeId":"charge-1","occurredAt":"2026-08-28T11:59:00Z"}
                """;
        var event = normalizer.normalize("fake", raw);
        assertThat(event.providerEventId()).isEqualTo("evt-1");
        assertThat(event.eventType()).isEqualTo("PAYMENT_CONFIRMED");
    }

    @Test
    void asaasReceivedMapsToCanonicalPaymentConfirmed() throws Exception {
        UUID orderId = UUID.randomUUID();
        String raw = """
                {"id":"evt_123","event":"PAYMENT_RECEIVED","dateCreated":"2024-06-12 16:45:03",
                 "payment":{"id":"pay_080225913252","externalReference":"%s","status":"RECEIVED","value":100}}
                """.formatted(orderId);

        var event = normalizer.normalize("asaas", raw);

        assertThat(event.providerEventId()).isEqualTo("evt_123");
        assertThat(event.eventType()).isEqualTo("PAYMENT_CONFIRMED");
        assertThat(event.chargeId()).isEqualTo(orderId);
        assertThat(event.providerChargeId()).isEqualTo("pay_080225913252");
        assertThat(event.occurredAt()).isEqualTo(Instant.parse("2024-06-12T19:45:03Z"));
    }

    @Test
    void asaasOverdueMapsToCanonicalPaymentExpired() throws Exception {
        UUID orderId = UUID.randomUUID();
        String raw = """
                {"id":"evt_456","event":"PAYMENT_OVERDUE","dateCreated":"2024-06-12 16:45:03",
                 "payment":{"id":"pay_2","externalReference":"%s","status":"OVERDUE","value":100}}
                """.formatted(orderId);

        assertThat(normalizer.normalize("asaas", raw).eventType()).isEqualTo("PAYMENT_EXPIRED");
    }

    @Test
    void asaasUnmappedEventKeepsPrefixInsteadOfGuessingAnEffect() throws Exception {
        UUID orderId = UUID.randomUUID();
        String raw = """
                {"id":"evt_789","event":"PAYMENT_REFUNDED","dateCreated":"2024-06-12 16:45:03",
                 "payment":{"id":"pay_3","externalReference":"%s","status":"REFUNDED","value":100}}
                """.formatted(orderId);

        assertThat(normalizer.normalize("asaas", raw).eventType()).isEqualTo("ASAAS_PAYMENT_REFUNDED");
    }

    @Test
    void asaasWithoutExternalReferenceFailsInsteadOfSilentlyDropping() {
        String raw = """
                {"id":"evt_1","event":"PAYMENT_RECEIVED","dateCreated":"2024-06-12 16:45:03",
                 "payment":{"id":"pay_1","externalReference":"not-a-uuid","status":"RECEIVED","value":100}}
                """;
        assertThatThrownBy(() -> normalizer.normalize("asaas", raw)).isInstanceOf(IllegalArgumentException.class);
    }
}
