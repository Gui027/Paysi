package com.paysi.webhook.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paysi.core.error.ConflictException;
import com.paysi.core.error.NotFoundException;
import com.paysi.core.error.ValidationException;
import com.paysi.security.mfa.port.SecretProtector;
import com.paysi.webhook.app.WebhookPanelModels.LogFilter;
import com.paysi.webhook.domain.OutboxEvent;
import com.paysi.webhook.domain.WebhookEndpoint;
import com.paysi.webhook.port.WebhookQueryRepository;
import com.paysi.webhook.port.WebhookRepository;
import com.paysi.webhook.port.WebhookSender;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebhookPanelTest {
    private static final Instant NOW = Instant.parse("2026-09-26T12:00:00Z");
    private static final UUID ACCOUNT = UUID.randomUUID();
    private static final UUID EVENT = UUID.randomUUID();
    private static final UUID ENDPOINT = UUID.randomUUID();
    private static final UUID PRODUCT = UUID.randomUUID();
    private static final UUID OTHER_PRODUCT = UUID.randomUUID();

    private record Fixture(WebhookDeliveryService service, WebhookRepository repository, WebhookSender sender) { }

    private static Fixture fixture(WebhookSender.SendResult result) throws Exception {
        WebhookRepository repository = mock(WebhookRepository.class);
        WebhookSender sender = mock(WebhookSender.class);
        when(sender.send(any(), any(), any())).thenReturn(result);
        SecretProtector secrets = mock(SecretProtector.class);
        when(secrets.decrypt(any())).thenAnswer(call -> call.getArgument(0));
        var urls = new WebhookUrlPolicy(host -> new InetAddress[] {InetAddress.getByName("8.8.8.8")});
        var service = new WebhookDeliveryService(repository, sender, new WebhookSigner(), urls, secrets, new ObjectMapper(), Clock.fixed(NOW, ZoneOffset.UTC));
        return new Fixture(service, repository, sender);
    }

    private static OutboxEvent event(String payload) {
        return new OutboxEvent(EVENT, ACCOUNT, "PAYMENT.APPROVED", payload, NOW.minusSeconds(10));
    }

    private static WebhookEndpoint endpoint(UUID productId, boolean enabled) {
        return new WebhookEndpoint(ENDPOINT, ACCOUNT, "https://hooks.example.com/events", Set.of("PAYMENT.APPROVED"), enabled,
                "secret".getBytes(), null, null, NOW.minusSeconds(100), "ERP", productId);
    }

    @Test
    void productFilterOnlyDeliversEventsOfThatProduct() throws Exception {
        var fixture = fixture(new WebhookSender.SendResult(200, null));
        when(fixture.repository.claimOutbox(any(), any(), any(), org.mockito.ArgumentMatchers.eq(10)))
                .thenReturn(List.of(event("{\"productId\":\"" + OTHER_PRODUCT + "\"}")));
        when(fixture.repository.activeEndpoints(any(), any())).thenReturn(List.of(endpoint(PRODUCT, true)));
        fixture.service.publish(10);
        verify(fixture.sender, never()).send(any(), any(), any());

        when(fixture.repository.claimOutbox(any(), any(), any(), org.mockito.ArgumentMatchers.eq(10)))
                .thenReturn(List.of(event("{\"productId\":\"" + PRODUCT + "\"}")));
        when(fixture.repository.nextAttempt(any(), any())).thenReturn(1);
        fixture.service.publish(10);
        verify(fixture.sender).send(any(), any(), any());
    }

    @Test
    void eventsWithoutProductIdAreResolvedThroughTheCharge() throws Exception {
        var fixture = fixture(new WebhookSender.SendResult(200, null));
        UUID charge = UUID.randomUUID();
        when(fixture.repository.claimOutbox(any(), any(), any(), org.mockito.ArgumentMatchers.eq(10)))
                .thenReturn(List.of(event("{\"chargeId\":\"" + charge + "\"}")));
        when(fixture.repository.activeEndpoints(any(), any())).thenReturn(List.of(endpoint(PRODUCT, true)));
        when(fixture.repository.nextAttempt(any(), any())).thenReturn(1);
        when(fixture.repository.productOfCharge(charge)).thenReturn(Optional.of(PRODUCT));
        fixture.service.publish(10);
        verify(fixture.sender).send(any(), any(), any());
    }

    @Test
    void deliveryStoresRequestAndTruncatedResponseForTheLogs() throws Exception {
        var fixture = fixture(new WebhookSender.SendResult(500, "HTTP_500", "x".repeat(6000)));
        when(fixture.repository.claimOutbox(any(), any(), any(), org.mockito.ArgumentMatchers.eq(10))).thenReturn(List.of(event("{\"a\":1}")));
        when(fixture.repository.activeEndpoints(any(), any())).thenReturn(List.of(endpoint(null, true)));
        when(fixture.repository.nextAttempt(any(), any())).thenReturn(1);
        fixture.service.publish(10);
        verify(fixture.repository).attachDeliveryDetails(any(), eq("https://hooks.example.com/events"), contains(EVENT.toString()),
                argThat(body -> body.length() == 4000));
    }

    @Test
    void resendToEndpointTargetsOnlyThatEndpointAndRefusesDisabledOnes() throws Exception {
        var fixture = fixture(new WebhookSender.SendResult(200, null));
        when(fixture.repository.findEvent(ACCOUNT, EVENT)).thenReturn(Optional.of(event("{\"a\":1}")));
        when(fixture.repository.findEndpoint(ACCOUNT, ENDPOINT)).thenReturn(Optional.of(endpoint(null, true)));
        when(fixture.repository.nextAttempt(any(), any())).thenReturn(2);
        fixture.service.resendToEndpoint(ACCOUNT, ENDPOINT, EVENT);
        verify(fixture.sender).send(any(), contains(EVENT.toString()), any());
        verify(fixture.repository, never()).activeEndpoints(any(), anyString());

        when(fixture.repository.findEndpoint(ACCOUNT, ENDPOINT)).thenReturn(Optional.of(endpoint(null, false)));
        assertThatThrownBy(() -> fixture.service.resendToEndpoint(ACCOUNT, ENDPOINT, EVENT)).isInstanceOf(ConflictException.class);
        when(fixture.repository.findEvent(ACCOUNT, EVENT)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> fixture.service.resendToEndpoint(ACCOUNT, ENDPOINT, EVENT)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void bulkResendValidatesTheSelectionSize() throws Exception {
        var fixture = fixture(new WebhookSender.SendResult(200, null));
        assertThatThrownBy(() -> fixture.service.resendManyToEndpoint(ACCOUNT, ENDPOINT, List.of())).isInstanceOf(ValidationException.class);
        List<UUID> many = java.util.stream.IntStream.range(0, 51).mapToObj(i -> UUID.randomUUID()).toList();
        assertThatThrownBy(() -> fixture.service.resendManyToEndpoint(ACCOUNT, ENDPOINT, many)).isInstanceOf(ValidationException.class);
        when(fixture.repository.findEvent(any(), any())).thenReturn(Optional.of(event("{\"a\":1}")));
        when(fixture.repository.findEndpoint(ACCOUNT, ENDPOINT)).thenReturn(Optional.of(endpoint(null, true)));
        when(fixture.repository.nextAttempt(any(), any())).thenReturn(1);
        assertThat(fixture.service.resendManyToEndpoint(ACCOUNT, ENDPOINT, List.of(UUID.randomUUID(), UUID.randomUUID()))).isEqualTo(2);
    }

    @Test
    void testEventIsSignedOnlyWhenTheEndpointIsKnownAndUsesTheUrlPolicy() throws Exception {
        var fixture = fixture(new WebhookSender.SendResult(200, null, "ok"));
        var unsigned = fixture.service.test(ACCOUNT, "https://hooks.example.com/x", null);
        assertThat(unsigned.success()).isTrue();
        assertThat(unsigned.statusCode()).isEqualTo(200);
        assertThat(unsigned.responseBody()).isEqualTo("ok");
        verify(fixture.sender).send(eq("https://hooks.example.com/x"), contains("WEBHOOK.TEST"), argThat(headers -> !headers.containsKey("X-Paysi-Signature") && "true".equals(headers.get("X-Paysi-Test"))));

        when(fixture.repository.findEndpoint(ACCOUNT, ENDPOINT)).thenReturn(Optional.of(endpoint(null, true)));
        fixture.service.test(ACCOUNT, "https://hooks.example.com/x", ENDPOINT);
        verify(fixture.sender).send(any(), any(), argThat(headers -> headers.containsKey("X-Paysi-Signature")));

        assertThatThrownBy(() -> fixture.service.test(ACCOUNT, "http://localhost/x", null)).isInstanceOf(ValidationException.class);
    }

    @Test
    void panelFilterValidatesEventsDatesAndSearch() {
        var service = new WebhookPanelService(mock(WebhookQueryRepository.class));
        LogFilter filter = service.filter(List.of("payment.approved", " "), "  ana ", "2026-09-01", "2026-09-30");
        assertThat(filter.eventTypes()).containsExactly("PAYMENT.APPROVED");
        assertThat(filter.query()).isEqualTo("ana");
        assertThatThrownBy(() -> service.filter(List.of("x"), null, null, null)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> service.filter(null, null, "2026-09-30", "2026-09-01")).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> service.filter(null, null, "30/09/2026", null)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> service.filter(null, "x".repeat(121), null, null)).isInstanceOf(ValidationException.class);
    }

    @Test
    void statusOfClassifiesSuccessRetryAndFailure() {
        assertThat(WebhookPanelModels.statusOf(200, null, null)).isEqualTo("SUCCESS");
        assertThat(WebhookPanelModels.statusOf(500, "HTTP_500", NOW)).isEqualTo("RETRYING");
        assertThat(WebhookPanelModels.statusOf(500, "HTTP_500", null)).isEqualTo("FAILED");
        assertThat(WebhookPanelModels.statusOf(null, "ConnectException", null)).isEqualTo("FAILED");
    }

    @Test
    void endpointServiceRequiresNameAndAProductOfTheSeller() {
        WebhookRepository repository = mock(WebhookRepository.class);
        SecretProtector secrets = mock(SecretProtector.class);
        when(secrets.encrypt(any())).thenReturn(new byte[] {1});
        var service = new WebhookEndpointService(repository, new WebhookUrlPolicy(host -> new InetAddress[] {InetAddress.getByAddress(new byte[] {8, 8, 8, 8})}), secrets);
        assertThatThrownBy(() -> service.create(ACCOUNT, " ", null, "https://hooks.example.com/x", Set.of("PAYMENT.APPROVED"), true)).isInstanceOf(ValidationException.class);
        when(repository.productBelongsTo(ACCOUNT, PRODUCT)).thenReturn(false);
        assertThatThrownBy(() -> service.create(ACCOUNT, "ERP", PRODUCT, "https://hooks.example.com/x", Set.of("PAYMENT.APPROVED"), true)).isInstanceOf(ValidationException.class);
    }
}
