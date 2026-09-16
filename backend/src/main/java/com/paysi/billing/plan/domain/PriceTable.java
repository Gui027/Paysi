package com.paysi.billing.plan.domain;

import com.paysi.payment.split.Plan;

/**
 * Tabela de preço vigente dos planos comerciais. Provisório: a definição
 * comercial de quanto cobrar por Escala pertence ao time de produto —
 * fixamos um valor único (R$ 199,00/mês) até existir um cartão para isso.
 */
public final class PriceTable {
    public static final String VERSION = "v1-provisional";
    private static final long ESCALA_PRICE_CENTS = 19_900;

    private PriceTable() {
    }

    public static long priceFor(Plan plan) {
        return plan == Plan.ESCALA ? ESCALA_PRICE_CENTS : 0;
    }
}
