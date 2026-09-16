package com.paysi.checkout.pricing.web.dto;

import com.paysi.catalog.offer.domain.OfferPaymentMethod;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Entrada da simulação. Não há campo de preço: o bruto é sempre relido da oferta
 * (documento 5, passo 5).
 */
public record SimulationRequest(
        @NotNull OfferPaymentMethod method,
        Integer installments,
        @Size(max = 32) String coupon
) {
    public int installmentsOrOne() {
        return installments == null ? 1 : installments;
    }
}
