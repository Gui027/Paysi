package com.paysi.billing.plan.web.dto;

import jakarta.validation.constraints.NotBlank;

public record PlanChangeRequest(@NotBlank String plan, String cardToken) {
}
