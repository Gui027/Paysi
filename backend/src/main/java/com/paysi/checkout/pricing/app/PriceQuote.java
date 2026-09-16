package com.paysi.checkout.pricing.app;

import com.paysi.catalog.coupon.app.CouponDiscount;
import com.paysi.catalog.offer.domain.OfferPaymentMethod;

import java.time.Instant;

/**
 * Memória de cálculo do servidor. Todo valor é inteiro em centavos e nenhum deles
 * veio do navegador: o bruto é relido da oferta e o desconto é resolvido pelo cupom.
 *
 * @param discount cupom aplicado; carrega o {@code couponId} quando a unidade já foi
 *                 reservada, e vem vazio na simulação
 */
public record PriceQuote(
        long grossCents,
        long discountCents,
        long paidCents,
        OfferPaymentMethod method,
        int installments,
        long feesCents,
        long commissionCents,
        long sellerCents,
        Instant availableAt,
        CouponDiscount discount
) {
    public String couponCode() {
        return discount.code();
    }
}
