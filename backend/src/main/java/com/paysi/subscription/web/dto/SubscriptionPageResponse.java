package com.paysi.subscription.web.dto;

import com.paysi.subscription.app.SubscriptionPage;

import java.util.List;

public record SubscriptionPageResponse(List<SubscriptionResponse> items, String nextCursor) {
    public static SubscriptionPageResponse from(SubscriptionPage page) {
        return new SubscriptionPageResponse(
                page.items().stream().map(SubscriptionResponse::from).toList(), page.nextCursor());
    }
}
