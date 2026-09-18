package com.paysi.subscription.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paysi.affiliate.app.CommissionService;
import com.paysi.ledger.app.SaleLedgerService;
import com.paysi.catalog.offer.domain.*;
import com.paysi.catalog.offer.port.OfferRepository;
import com.paysi.catalog.product.domain.ChargeType;
import com.paysi.catalog.product.domain.Segment;
import com.paysi.core.error.ConflictException;
import com.paysi.core.error.NotFoundException;
import com.paysi.core.error.ValidationException;
import com.paysi.identity.port.PlatformPlanReader;
import com.paysi.payment.provider.*;
import com.paysi.subscription.domain.SubscriptionStatus;
import com.paysi.subscription.port.SubscriptionRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SubscriptionServiceTest {
    private static final UUID OFFER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID SELLER = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final Instant NOW = Instant.parse("2026-08-28T12:00:00Z");

    @Test
    void immediateChargeActivatesSubscriptionWhenApproved() {
        var fixture = fixture(offer(0, true));
        when(fixture.provider.charge(any())).thenReturn(approved());

        var result = fixture.service.create(command("tok_1"));

        assertThat(result.idempotentReplay()).isFalse();
        verify(fixture.repository).insertSubscription(argThat(s -> s.status() == SubscriptionStatus.ACTIVE
                && s.cycleNumber() == 1));
        verify(fixture.repository).updateSubscriptionCycle(any(), eq("ACTIVE"),
                eq(NOW.atZone(java.time.ZoneOffset.UTC).plusMonths(1).toInstant()));
        verify(fixture.repository).markOrderStatus(any(), eq("PAID"), any());
    }

    @Test
    void declinedChargeLeavesSubscriptionPastDue() {
        var fixture = fixture(offer(0, true));
        when(fixture.provider.charge(any())).thenReturn(declined());

        fixture.service.create(command("tok_1"));

        verify(fixture.repository).updateSubscriptionCycle(any(), eq("PAST_DUE"), isNull());
        verify(fixture.repository).markOrderStatus(any(), eq("FAILED"), isNull());
    }

    @Test
    void trialWithoutCardSkipsChargeAndProvider() {
        var fixture = fixture(offer(7, false));

        var result = fixture.service.create(command(null));

        assertThat(result.idempotentReplay()).isFalse();
        verify(fixture.repository).insertSubscription(argThat(s -> s.status() == SubscriptionStatus.TRIAL
                && s.trialEndsAt().equals(NOW.plusSeconds(7L * 86400))));
        verifyNoInteractions(fixture.provider);
    }

    @Test
    void trialRequiringCardStillValidatesToken() {
        var fixture = fixture(offer(7, true));
        assertThatThrownBy(() -> fixture.service.create(command(null)))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void sameIdempotencyKeyAndBodyReplaysWithoutChargingTwice() {
        var fixture = fixture(offer(0, true));
        var command = command("tok_1");
        String hash = SubscriptionService.hash(command);
        UUID existingSubscription = UUID.randomUUID();
        when(fixture.repository.findOrderByIdempotency(OFFER, "idem-1"))
                .thenReturn(Optional.of(new SubscriptionRepository.OrderReplay(UUID.randomUUID(), existingSubscription, hash)));

        var result = fixture.service.create(command);

        assertThat(result.idempotentReplay()).isTrue();
        assertThat(result.subscriptionId()).isEqualTo(existingSubscription);
        verifyNoInteractions(fixture.provider);
        verify(fixture.repository, never())
                .insertOrder(any(), any(), any(), any(), any(), anyLong(), any(), any(), any(), any());
    }

    @Test
    void sameIdempotencyKeyDifferentBodyConflicts() {
        var fixture = fixture(offer(0, true));
        when(fixture.repository.findOrderByIdempotency(OFFER, "idem-1"))
                .thenReturn(Optional.of(new SubscriptionRepository.OrderReplay(UUID.randomUUID(), UUID.randomUUID(), "different-hash")));

        assertThatThrownBy(() -> fixture.service.create(command("tok_1")))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void cancelIsIdempotentWhenAlreadyRequested() {
        var fixture = fixture(offer(0, true));
        UUID subscriptionId = UUID.randomUUID();
        when(fixture.repository.requestCancelAtPeriodEnd(SELLER, subscriptionId, NOW)).thenReturn(false);
        when(fixture.repository.findOwned(SELLER, subscriptionId)).thenReturn(Optional.of(
                new com.paysi.subscription.domain.Subscription(subscriptionId, UUID.randomUUID(), OFFER,
                        SubscriptionStatus.ACTIVE, 2, null, NOW, NOW, "tok_1", NOW)));

        fixture.service.cancelAtPeriodEnd(SELLER, subscriptionId);

        verify(fixture.repository).requestCancelAtPeriodEnd(SELLER, subscriptionId, NOW);
    }

    /**
     * AM-12 / checklist #14: cancelar a assinatura de outro vendedor não pode
     * nem parecer bem-sucedido nem vazar o estado dela — precisa devolver o
     * mesmo 404 de "não existe", porque {@code requestCancelAtPeriodEnd} já é
     * escopado por {@code sellerId} no {@code WHERE} e por isso não muda nada
     * quando a assinatura pertence a outra conta.
     */
    @Test
    void cancelingAnotherSellersSubscriptionIsNotFoundNotForbidden() {
        var fixture = fixture(offer(0, true));
        UUID subscriptionId = UUID.randomUUID();
        UUID otherSeller = UUID.randomUUID();
        when(fixture.repository.requestCancelAtPeriodEnd(otherSeller, subscriptionId, NOW)).thenReturn(false);
        when(fixture.repository.findOwned(otherSeller, subscriptionId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> fixture.service.cancelAtPeriodEnd(otherSeller, subscriptionId))
                .isInstanceOf(NotFoundException.class);
    }

    /** Mesma garantia da leitura: consultar detalhe de assinatura alheia é 404. */
    @Test
    void readingAnotherSellersSubscriptionDetailIsNotFound() {
        var fixture = fixture(offer(0, true));
        UUID subscriptionId = UUID.randomUUID();
        UUID otherSeller = UUID.randomUUID();
        when(fixture.repository.findOwned(otherSeller, subscriptionId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> fixture.service.detail(otherSeller, subscriptionId))
                .isInstanceOf(NotFoundException.class);
        verify(fixture.repository, never()).listCharges(any());
    }

    private static Fixture fixture(Offer offerValue) {
        var repository = mock(SubscriptionRepository.class);
        var offers = mock(OfferRepository.class);
        var plans = mock(PlatformPlanReader.class);
        var provider = mock(PaymentProvider.class);
        var commissions = mock(CommissionService.class);
        var saleLedger = mock(SaleLedgerService.class);
        when(offers.findPublishedBySlug("plano-mensal")).thenReturn(Optional.of(offerValue));
        when(repository.sellerIdForOffer(OFFER)).thenReturn(Optional.of(SELLER));
        when(repository.findOrderByIdempotency(any(), any())).thenReturn(Optional.empty());
        when(repository.insertOrder(any(), any(), any(), any(), any(), anyLong(), any(), any(), any(), any()))
                .thenReturn(true);
        when(repository.findBuyer(any(), any())).thenReturn(Optional.empty());
        when(repository.insertBuyer(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(plans.currentPlan(SELLER)).thenReturn("TRANSACIONAL");
        when(commissions.resolveForCharge(any(), any(), anyInt())).thenReturn(Optional.empty());
        var service = new SubscriptionService(repository, offers, plans, provider, commissions, saleLedger,
                new ObjectMapper(), Clock.fixed(NOW, ZoneOffset.UTC));
        return new Fixture(service, repository, provider);
    }

    private static Offer offer(int trialDays, boolean trialRequiresCard) {
        return new Offer(OFFER, UUID.randomUUID(), ChargeType.SUBSCRIPTION, Segment.SAAS, "plano-mensal",
                10_000, BillingCycle.MONTHLY, trialDays, trialRequiresCard, 7, 1, 3, 5,
                Set.of(OfferPaymentMethod.CARD), OfferPayoutDelay.D2, OfferStatus.PUBLISHED, null, NOW, NOW);
    }

    private static CreateSubscriptionCommand command(String cardToken) {
        return new CreateSubscriptionCommand("plano-mensal", "Comprador Teste", "buyer@example.com", "PF",
                "52998224725", null, null, null, cardToken, "CARD", null, "idem-1");
    }

    private static ProviderPaymentResult approved() {
        return new ProviderPaymentResult("prov-1", ProviderChargeStatus.APPROVED, null, 199,
                java.util.List.of(), new ProviderThreeDs("NOT_APPLICABLE", null, null), null, false);
    }

    private static ProviderPaymentResult declined() {
        return new ProviderPaymentResult("prov-1", ProviderChargeStatus.DECLINED, null, 199,
                java.util.List.of(), new ProviderThreeDs("NOT_APPLICABLE", null, null), null, false);
    }

    private record Fixture(SubscriptionService service, SubscriptionRepository repository, PaymentProvider provider) {
    }
}
