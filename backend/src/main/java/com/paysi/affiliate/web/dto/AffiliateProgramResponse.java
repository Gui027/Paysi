package com.paysi.affiliate.web.dto;

import com.paysi.affiliate.domain.AffiliateProgram;
import com.paysi.affiliate.domain.AffiliationRecurrence;

import java.util.UUID;

public record AffiliateProgramResponse(
        UUID productId,
        int commissionBps,
        AffiliationRecurrence recurrence,
        boolean autoApprove,
        String supportEmail,
        String description
) {
    public static AffiliateProgramResponse from(AffiliateProgram program) {
        return new AffiliateProgramResponse(program.productId(), program.commissionBps(), program.recurrence(),
                program.autoApprove(), program.supportEmail(), program.description());
    }
}
