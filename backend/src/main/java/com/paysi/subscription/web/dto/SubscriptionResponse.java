package com.paysi.subscription.web.dto;

import com.paysi.subscription.domain.Subscription;

import java.time.Instant;
import java.util.UUID;

public record SubscriptionResponse(
        UUID id,
        UUID orderId,
        UUID offerId,
        String status,
        int cycleNumber,
        Instant trialEndsAt,
        Instant nextChargeAt,
        Instant canceledAt,
        boolean cancelPending,
        boolean hasPaymentMethod,
        Instant createdAt
) {
    public static SubscriptionResponse from(Subscription subscription) {
        return new SubscriptionResponse(subscription.id(), subscription.orderId(), subscription.offerId(),
                subscription.status().name(), subscription.cycleNumber(), subscription.trialEndsAt(),
                subscription.nextChargeAt(), subscription.canceledAt(), subscription.cancelPending(),
                subscription.providerToken() != null && !subscription.providerToken().isBlank(),
                subscription.createdAt());
    }
}
