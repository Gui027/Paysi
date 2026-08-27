package com.paysi.identity.kyc.webhook.port;

import com.paysi.identity.domain.KycStatus;
import com.paysi.identity.kyc.domain.KycRequirement;
import java.util.*;

public interface KycWebhookStore {
    boolean receive(String eventId,String payload);
    String lockStatus(String eventId);
    Optional<AccountKycState> lockAccount(UUID accountId);
    void apply(UUID accountId,KycStatus status,String providerAccountId,List<KycRequirement> requirements);
    void markProcessed(String eventId);
    record AccountKycState(KycStatus status,String providerAccountId) { }
}
