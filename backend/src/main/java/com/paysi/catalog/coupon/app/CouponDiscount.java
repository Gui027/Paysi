package com.paysi.catalog.coupon.app;

import java.util.UUID;

/**
 * Desconto já resolvido pelo servidor. O checkout nunca envia valor: o código do cupom
 * entra, o desconto em centavos sai (RF-027).
 *
 * @param couponId preenchido apenas quando a unidade foi efetivamente reservada; a
 *                 simulação devolve {@code null} porque não consome nada
 * @param maxPerBuyer limite carregado junto para que a conferência posterior não
 *                    precise reler o cupom
 */
public record CouponDiscount(UUID couponId, String code, long discountCents, int maxPerBuyer) {

    /** Ausência de cupom, para que quem chama não precise tratar {@code null}. */
    public static CouponDiscount none() {
        return new CouponDiscount(null, null, 0, 0);
    }

    /** Verdadeiro somente quando há unidade reservada a confirmar. */
    public boolean reserved() {
        return couponId != null;
    }
}
