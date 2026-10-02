package com.paysi.checkout.pricing.web.dto;

import com.paysi.catalog.offer.domain.OfferPaymentMethod;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Entrada da simulação. O valor só é aceito em ofertas flexíveis e é validado contra
 * o mínimo armazenado; em ofertas fixas, o bruto continua vindo da oferta.
 */
public record SimulationRequest(
        @NotNull OfferPaymentMethod method,
        Integer installments,
        @Size(max = 32) String coupon,
        @Min(200) Long amountCents
) {
    /** Mantém compatibilidade com simulações de ofertas fixas. */
    public SimulationRequest(OfferPaymentMethod method, Integer installments, String coupon) {
        this(method, installments, coupon, null);
    }

    public int installmentsOrOne() {
        return installments == null ? 1 : installments;
    }
}
