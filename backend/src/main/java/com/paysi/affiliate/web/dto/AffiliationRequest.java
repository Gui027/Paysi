package com.paysi.affiliate.web.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AffiliationRequest(@NotNull UUID productId) {
}
