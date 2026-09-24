package com.paysi.payment.inbox.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paysi.payment.inbox.port.ChargeLookup;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DispatchingProviderEventNormalizerTest {
    private static final UUID CHARGE_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private final ChargeLookup knownCharge = providerId ->
            "pay_080225913252".equals(providerId) ? Optional.of(CHARGE_ID) : Optional.empty();
    private final DispatchingProviderEventNormalizer normalizer =
            new DispatchingProviderEventNormalizer(new ObjectMapper().findAndRegisterModules(), knownCharge);

    private static String asaasEvent(String event, String paymentId) {
        return """
                {"id":"evt_1","event":"%s","dateCreated":"2024-06-12 16:45:03",
                 "payment":{"id":"%s","externalReference":"%s","status":"RECEIVED","value":100}}
                """.formatted(event, paymentId, UUID.randomUUID());
    }

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
    void asaasReceivedMapsToConfirmedAndResolvesChargeByProviderId() throws Exception {
        var event = normalizer.normalize("asaas", asaasEvent("PAYMENT_RECEIVED", "pay_080225913252"));

        assertThat(event.providerEventId()).isEqualTo("evt_1");
        assertThat(event.eventType()).isEqualTo("PAYMENT_CONFIRMED");
        assertThat(event.chargeId()).isEqualTo(CHARGE_ID);
        assertThat(event.providerChargeId()).isEqualTo("pay_080225913252");
        assertThat(event.occurredAt()).isEqualTo(Instant.parse("2024-06-12T19:45:03Z"));
    }

    @Test
    void asaasOverdueMapsToExpired() throws Exception {
        assertThat(normalizer.normalize("asaas", asaasEvent("PAYMENT_OVERDUE", "pay_080225913252")).eventType())
                .isEqualTo("PAYMENT_EXPIRED");
    }

    @Test
    void asaasEventWithoutEffectIsAcceptedEvenIfChargeIsNotSavedYet() throws Exception {
        // PAYMENT_CREATED chega enquanto a transação que grava provider_charge_id ainda está aberta.
        var event = normalizer.normalize("asaas", asaasEvent("PAYMENT_CREATED", "pay_not_saved_yet"));

        assertThat(event.eventType()).isEqualTo("ASAAS_PAYMENT_CREATED");
        assertThat(event.chargeId()).isEqualTo(new UUID(0L, 0L));
    }

    @Test
    void asaasRefundKeepsPrefixInsteadOfGuessingAnEffect() throws Exception {
        assertThat(normalizer.normalize("asaas", asaasEvent("PAYMENT_REFUNDED", "pay_080225913252")).eventType())
                .isEqualTo("ASAAS_PAYMENT_REFUNDED");
    }

    @Test
    void asaasConfirmationForUnknownChargeIsRejectedSoAsaasRetries() {
        assertThatThrownBy(() -> normalizer.normalize("asaas", asaasEvent("PAYMENT_RECEIVED", "pay_unknown")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
