package com.paysi.payment.simulation.web.dto;

import com.paysi.payment.simulation.app.OfferSimulationService.OfferSimulation;

import java.time.Instant;

public record OfferSimulationResponse(
        long grossCents,
        long discountCents,
        long paidCents,
        long platformFeeCents,
        long providerCostCents,
        long commissionCents,
        long sellerCents,
        Instant availableAt
) {
    public static OfferSimulationResponse from(OfferSimulation simulation) {
        return new OfferSimulationResponse(simulation.grossCents(), simulation.discountCents(),
                simulation.paidCents(), simulation.platformFeeCents(), simulation.providerCostCents(),
                simulation.commissionCents(), simulation.sellerCents(), simulation.availableAt());
    }
}
