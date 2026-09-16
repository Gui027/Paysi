package com.paysi.subscription.app;

import com.paysi.subscription.domain.Subscription;

import java.util.List;

public record SubscriptionPage(List<Subscription> items, String nextCursor) {
}
