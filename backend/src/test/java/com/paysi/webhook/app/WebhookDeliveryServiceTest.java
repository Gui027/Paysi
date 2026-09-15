package com.paysi.webhook.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paysi.security.mfa.port.SecretProtector;
import com.paysi.webhook.domain.OutboxEvent;
import com.paysi.webhook.domain.WebhookDelivery;
import com.paysi.webhook.domain.WebhookEndpoint;
import com.paysi.webhook.port.WebhookRepository;
import com.paysi.webhook.port.WebhookSender;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.net.InetAddress;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WebhookDeliveryServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-11T12:00:00Z");
    private static final UUID ACCOUNT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID EVENT = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID ENDPOINT = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Test void publishesWithStableEventIdAndHmacHeaders() throws Exception {
        var fixture = fixture(new WebhookSender.SendResult(204, null));
        when(fixture.repository.claimOutbox(any(), any(), any(), eq(10))).thenReturn(List.of(event()));
        when(fixture.repository.activeEndpoints(ACCOUNT, "PAYMENT.CONFIRMED")).thenReturn(List.of(endpoint(null)));
        when(fixture.repository.nextAttempt(EVENT, ENDPOINT)).thenReturn(1);
        assertThat(fixture.service.publish(10)).isEqualTo(1);
        verify(fixture.sender).send(eq("https://hooks.example.com/events"), contains(EVENT.toString()), argThat(headers -> EVENT.toString().equals(headers.get("X-Paysi-Event-Id")) && headers.get("X-Paysi-Signature").startsWith("v1=")));
        verify(fixture.repository).markPublished(eq(EVENT), any(), eq(NOW));
    }

    @Test void schedulesRetriesAtTheRequiredIntervalsAndStopsAfterFifthRetry() throws Exception {
        var fixture = fixture(new WebhookSender.SendResult(500, "HTTP_500"));
        when(fixture.repository.claimOutbox(any(), any(), any(), eq(10))).thenReturn(List.of(event()));
        when(fixture.repository.activeEndpoints(any(), any())).thenReturn(List.of(endpoint(null)));
        when(fixture.repository.nextAttempt(EVENT, ENDPOINT)).thenReturn(1);
        fixture.service.publish(10);
        var delivery = ArgumentCaptor.forClass(WebhookDelivery.class);
        verify(fixture.repository).insertDelivery(delivery.capture());
        assertThat(delivery.getValue().nextRetryAt()).isEqualTo(NOW.plusSeconds(60));

        reset(fixture.repository);
        when(fixture.repository.claimOutbox(any(), any(), any(), eq(10))).thenReturn(List.of(event()));
        when(fixture.repository.activeEndpoints(any(), any())).thenReturn(List.of(endpoint(null)));
        when(fixture.repository.nextAttempt(EVENT, ENDPOINT)).thenReturn(6);
        fixture.service.publish(10);
        verify(fixture.repository).insertDelivery(delivery.capture());
        assertThat(delivery.getValue().nextRetryAt()).isNull();
    }

    @Test void emitsCurrentAndPreviousSignaturesDuringRotationWindow() throws Exception {
        var fixture = fixture(new WebhookSender.SendResult(200, null));
        when(fixture.repository.claimOutbox(any(), any(), any(), eq(1))).thenReturn(List.of(event()));
        when(fixture.repository.activeEndpoints(any(), any())).thenReturn(List.of(endpoint(NOW.minusSeconds(3600))));
        when(fixture.repository.nextAttempt(any(), any())).thenReturn(1);
        fixture.service.publish(1);
        verify(fixture.sender).send(any(), any(), argThat(headers -> headers.containsKey("X-Paysi-Signature") && headers.containsKey("X-Paysi-Signature-Previous")));
    }

    @Test void manualResendPreservesOriginalEventId() throws Exception {
        var fixture = fixture(new WebhookSender.SendResult(200, null));
        when(fixture.repository.findEvent(ACCOUNT, EVENT)).thenReturn(java.util.Optional.of(event()));
        when(fixture.repository.activeEndpoints(ACCOUNT, "PAYMENT.CONFIRMED")).thenReturn(List.of(endpoint(null)));
        when(fixture.repository.nextAttempt(EVENT, ENDPOINT)).thenReturn(3);
        fixture.service.resend(ACCOUNT, EVENT);
        verify(fixture.sender).send(any(), contains(EVENT.toString()), argThat(headers -> EVENT.toString().equals(headers.get("X-Paysi-Event-Id"))));
    }

    private static Fixture fixture(WebhookSender.SendResult result) throws Exception {
        WebhookRepository repository = mock(WebhookRepository.class);
        WebhookSender sender = mock(WebhookSender.class); when(sender.send(any(), any(), any())).thenReturn(result);
        SecretProtector secrets = mock(SecretProtector.class); when(secrets.decrypt(any())).thenAnswer(call -> call.getArgument(0));
        var urls = new WebhookUrlPolicy(host -> new InetAddress[]{InetAddress.getByName("8.8.8.8")});
        var service = new WebhookDeliveryService(repository, sender, new WebhookSigner(), urls, secrets, new ObjectMapper(), Clock.fixed(NOW, ZoneOffset.UTC));
        return new Fixture(service, repository, sender);
    }
    private static OutboxEvent event() { return new OutboxEvent(EVENT, ACCOUNT, "PAYMENT.CONFIRMED", "{\"chargeId\":\"one\"}", NOW.minusSeconds(10)); }
    private static WebhookEndpoint endpoint(Instant rotatedAt) { return new WebhookEndpoint(ENDPOINT, ACCOUNT, "https://hooks.example.com/events", Set.of("PAYMENT.CONFIRMED"), true, "current-secret".getBytes(), rotatedAt == null ? null : "previous-secret".getBytes(), rotatedAt, NOW.minusSeconds(100)); }
    private record Fixture(WebhookDeliveryService service, WebhookRepository repository, WebhookSender sender) { }
}
