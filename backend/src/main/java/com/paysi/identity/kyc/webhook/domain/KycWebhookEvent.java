package com.paysi.identity.kyc.webhook.domain;

import com.paysi.identity.kyc.domain.KycRequirement;
import java.util.List;
import java.util.UUID;

public record KycWebhookEvent(String providerEventId, UUID accountReference, KycWebhookStatus status,
                              List<KycRequirement> requirements) {
    public KycWebhookEvent {
        if(providerEventId==null||providerEventId.isBlank()||accountReference==null||status==null)throw new IllegalArgumentException("Invalid KYC webhook event");
        requirements=requirements==null?List.of():List.copyOf(requirements);
    }
}
