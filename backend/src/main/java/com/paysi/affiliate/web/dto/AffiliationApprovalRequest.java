package com.paysi.affiliate.web.dto;

import com.paysi.affiliate.domain.AffiliationRecurrence;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record AffiliationApprovalRequest(
        @Min(0) @Max(5000) int commissionBps,
        @NotNull AffiliationRecurrence recurrence
) {
}
