package com.paysi.subscription.web.dto;

import com.paysi.subscription.app.SubscriptionService;

import java.util.List;

public record SubscriptionDetailResponse(SubscriptionResponse subscription, List<SubscriptionChargeResponse> charges) {
    public static SubscriptionDetailResponse from(SubscriptionService.SubscriptionDetail detail) {
        return new SubscriptionDetailResponse(SubscriptionResponse.from(detail.subscription()),
                detail.charges().stream().map(SubscriptionChargeResponse::from).toList());
    }
}
