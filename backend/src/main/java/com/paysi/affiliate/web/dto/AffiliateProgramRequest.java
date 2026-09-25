package com.paysi.affiliate.web.dto;

import com.paysi.affiliate.domain.AffiliationRecurrence;
import jakarta.validation.constraints.NotNull;

public record AffiliateProgramRequest(
        @NotNull(message = "Comissão é obrigatória") Integer commissionBps,
        @NotNull(message = "Recorrência é obrigatória") AffiliationRecurrence recurrence,
        @NotNull(message = "Informe se a aprovação é automática") Boolean autoApprove,
        String supportEmail,
        String description
) {
}
