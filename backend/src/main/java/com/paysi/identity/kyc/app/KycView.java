package com.paysi.identity.kyc.app;

import com.paysi.identity.domain.KycStatus;
import com.paysi.identity.kyc.domain.KycRequirement;
import java.util.List;
import java.util.UUID;

public record KycView(UUID accountId, KycStatus kycStatus, String providerUrl, List<KycRequirement> requirements) {
}
