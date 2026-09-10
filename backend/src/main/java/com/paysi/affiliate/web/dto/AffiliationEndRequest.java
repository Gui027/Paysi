package com.paysi.affiliate.web.dto;

import com.paysi.affiliate.domain.AffiliationEndReason;
import jakarta.validation.constraints.NotNull;

public record AffiliationEndRequest(@NotNull AffiliationEndReason reason) {
}
