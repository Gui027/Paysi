package com.paysi.webhook.app;

import com.paysi.core.error.NotFoundException;
import com.paysi.security.mfa.port.SecretProtector;
import com.paysi.webhook.port.WebhookRepository;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class WebhookEndpointServiceTest {
    private static final UUID ACCOUNT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Instant NOW = Instant.parse("2026-09-11T12:00:00Z");

    @Test void createsEndpointAndReturnsSecretOnlyInCreationResponse() throws Exception {
        WebhookRepository repository = mock(WebhookRepository.class);
        SecretProtector protector = mock(SecretProtector.class);
        when(protector.encrypt(any())).thenAnswer(call -> call.getArgument(0));
        var service = service(repository, protector);
        var created = service.create(ACCOUNT, "https://hooks.example.com/events", Set.of("payment.confirmed"), true);
        assertThat(created.secret()).hasSizeGreaterThan(40);
        assertThat(created.endpoint().events()).containsExactly("PAYMENT.CONFIRMED");
        assertThat(created.endpoint().enabled()).isTrue();
        verify(repository).insertEndpoint(any());
    }

    @Test void keepsPreviousSecretValidForTwentyFourHoursOnRotation() throws Exception {
        WebhookRepository repository = mock(WebhookRepository.class);
        SecretProtector protector = mock(SecretProtector.class);
        when(protector.encrypt(any())).thenAnswer(call -> call.getArgument(0));
        when(repository.rotateSecret(eq(ACCOUNT), any(), any(), eq(NOW))).thenReturn(true);
        var service = service(repository, protector);
        var rotated = service.rotate(ACCOUNT, UUID.randomUUID());
        assertThat(rotated.previousSecretValidUntil()).isEqualTo(NOW.plusSeconds(86_400));
    }

    @Test void doesNotExposeEndpointFromAnotherAccount() throws Exception {
        WebhookRepository repository = mock(WebhookRepository.class);
        when(repository.findEndpoint(any(), any())).thenReturn(Optional.empty());
        var service = service(repository, mock(SecretProtector.class));
        assertThatThrownBy(() -> service.update(ACCOUNT, UUID.randomUUID(), "https://hooks.example.com", Set.of("SALE.PAID"), true)).isInstanceOf(NotFoundException.class);
    }

    private static WebhookEndpointService service(WebhookRepository repository, SecretProtector protector) throws Exception {
        var urls = new WebhookUrlPolicy(host -> new InetAddress[]{InetAddress.getByName("8.8.8.8")});
        return new WebhookEndpointService(repository, urls, protector, new SecureRandom(), Clock.fixed(NOW, ZoneOffset.UTC));
    }
}
