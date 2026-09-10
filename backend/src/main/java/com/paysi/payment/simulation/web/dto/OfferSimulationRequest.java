package com.paysi.payment.simulation.web.dto;

import com.paysi.catalog.offer.domain.OfferPaymentMethod;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record OfferSimulationRequest(
        @NotNull OfferPaymentMethod method,
        @NotNull @Min(1) @Max(12) Integer installments,
        @Size(max = 32) String couponCode
) {
}
