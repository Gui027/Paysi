package com.paysi.affiliate.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AffiliateClickRequest(
        @NotNull UUID productId,
        @NotNull UUID affiliateId,
        @NotBlank String visitorKey
) {
}
