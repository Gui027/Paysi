package com.paysi.apikey.web;

import com.paysi.apikey.app.ApiAuthService;
import com.paysi.apikey.app.ApiKeyModels;
import com.paysi.catalog.offer.domain.Offer;
import com.paysi.catalog.offer.port.OfferRepository;
import com.paysi.catalog.product.app.ProductService;
import com.paysi.payment.provider.CardTokenResult;
import com.paysi.payment.provider.CardTokenizer;
import com.paysi.security.ratelimit.app.CheckoutRateLimitGuard;
import com.paysi.subscription.app.CreateSubscriptionCommand;
import com.paysi.subscription.app.SubscriptionPage;
import com.paysi.subscription.app.SubscriptionService;
import com.paysi.subscription.domain.Subscription;
import com.paysi.subscription.domain.SubscriptionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PublicSubscriptionControllerTest {
    private static final UUID SELLER = UUID.randomUUID();
    private final ApiAuthService auth = mock(ApiAuthService.class);
    private final OfferRepository offers = mock(OfferRepository.class);
    private final ProductService products = mock(ProductService.class);
    private final SubscriptionService subscriptions = mock(SubscriptionService.class);
    private final CardTokenizer tokenizer = mock(CardTokenizer.class);
    private final CheckoutRateLimitGuard rateLimit = mock(CheckoutRateLimitGuard.class);
    private PublicSubscriptionController controller;

    @BeforeEach
    void setUp() {
        controller = new PublicSubscriptionController(auth, offers, products, subscriptions, tokenizer, rateLimit);
        when(auth.authenticate(any(), any(), any())).thenReturn(new ApiKeyModels.ApiPrincipal(
                SELLER, UUID.randomUUID(), List.of("subscriptions_read", "subscriptions_write")));
    }

    @Test
    void createsRecurringCardSubscriptionWithTokenizedCard() {
        UUID productId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();
        Offer offer = mock(Offer.class);
        when(offer.productId()).thenReturn(productId);
        when(offers.findPublishedBySlug("plano-pro")).thenReturn(Optional.of(offer));
        when(subscriptions.findReplay(any())).thenReturn(Optional.empty());
        when(tokenizer.tokenize(any())).thenReturn(new CardTokenResult("tok_provider", "VISA", "4242"));
        when(subscriptions.create(any())).thenReturn(
                new SubscriptionService.SubscriptionCreationResult(subscriptionId, false));
        var subscription = new Subscription(subscriptionId, UUID.randomUUID(), UUID.randomUUID(),
                SubscriptionStatus.ACTIVE, 1, null, Instant.parse("2026-12-10T15:00:00Z"), null,
                "tok_provider", Instant.parse("2026-11-10T15:00:00Z"));
        when(subscriptions.detail(SELLER, subscriptionId)).thenReturn(
                new SubscriptionService.SubscriptionDetail(subscription, List.of()));
        var buyer = new PublicSubscriptionController.BuyerRequest("Ana", "ana@example.com", "PF",
                "12345678909", null, null, null);
        var card = new PublicSubscriptionController.CardRequest("Ana", "4242424242424242", "12", "2030",
                "123", "01310100", "100", "11999999999");
        var request = new PublicSubscriptionController.CreateSubscriptionRequest("plano-pro", buyer,
                "CARD", "device-1", " 203.0.113.42 ", card);

        var response = controller.create("Bearer paysi_live_key", null, "sub-8472", request);

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().subscription().id()).isEqualTo(subscriptionId);
        verify(auth).authenticate("Bearer paysi_live_key", null, ApiKeyModels.SUBSCRIPTIONS_WRITE);
        verify(products).get(SELLER, productId);
        verify(rateLimit).checkOrderAttempt("203.0.113.42", "12345678909", "device-1");
        var command = ArgumentCaptor.forClass(CreateSubscriptionCommand.class);
        verify(subscriptions).create(command.capture());
        assertThat(command.getValue().cardToken()).isEqualTo("tok_provider");
    }

    @Test
    void idempotentReplayDoesNotTokenizeCardAgain() {
        UUID productId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();
        Offer offer = mock(Offer.class);
        when(offer.productId()).thenReturn(productId);
        when(offers.findPublishedBySlug("plano-pro")).thenReturn(Optional.of(offer));
        when(subscriptions.findReplay(any())).thenReturn(Optional.of(
                new SubscriptionService.SubscriptionCreationResult(subscriptionId, true)));
        var subscription = new Subscription(subscriptionId, UUID.randomUUID(), UUID.randomUUID(),
                SubscriptionStatus.ACTIVE, 1, null, Instant.parse("2026-12-10T15:00:00Z"), null,
                "tok_provider", Instant.parse("2026-11-10T15:00:00Z"));
        when(subscriptions.detail(SELLER, subscriptionId)).thenReturn(
                new SubscriptionService.SubscriptionDetail(subscription, List.of()));
        var buyer = new PublicSubscriptionController.BuyerRequest("Ana", "ana@example.com", "PF",
                "12345678909", null, null, null);
        var card = new PublicSubscriptionController.CardRequest("Ana", "4242424242424242", "12", "2030",
                "123", "01310100", "100", "11999999999");
        var request = new PublicSubscriptionController.CreateSubscriptionRequest("plano-pro", buyer,
                "CARD", "device-1", "203.0.113.42", card);

        var response = controller.create("Bearer paysi_live_key", null, "sub-8472", request);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        verifyNoInteractions(tokenizer, rateLimit);
        verify(subscriptions, never()).create(any());
    }

    @Test
    void readsListsAndCancelsOnlyWithTheCorrespondingScopes() {
        UUID subscriptionId = UUID.randomUUID();
        var subscription = new Subscription(subscriptionId, UUID.randomUUID(), UUID.randomUUID(),
                SubscriptionStatus.ACTIVE, 1, null, null, null, "tok", Instant.now());
        when(subscriptions.detail(SELLER, subscriptionId)).thenReturn(
                new SubscriptionService.SubscriptionDetail(subscription, List.of()));
        when(subscriptions.list(SELLER, null, 20)).thenReturn(new SubscriptionPage(List.of(subscription), null));

        assertThat(controller.detail("Bearer key", null, subscriptionId).subscription().id()).isEqualTo(subscriptionId);
        assertThat(controller.list("Bearer key", null, null, 20).items()).hasSize(1);
        assertThat(controller.cancel("Bearer key", null, subscriptionId).getStatusCode().value()).isEqualTo(204);

        verify(auth, times(2)).authenticate("Bearer key", null, ApiKeyModels.SUBSCRIPTIONS_READ);
        verify(auth).authenticate("Bearer key", null, ApiKeyModels.SUBSCRIPTIONS_WRITE);
        verify(subscriptions).cancelAtPeriodEnd(SELLER, subscriptionId);
    }
}
