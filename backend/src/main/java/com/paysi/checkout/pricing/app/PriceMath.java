package com.paysi.checkout.pricing.app;

import com.paysi.catalog.offer.domain.OfferPaymentMethod;
import com.paysi.payment.split.PaymentMethod;
import com.paysi.payment.split.Plan;
import com.paysi.payment.split.Split;
import com.paysi.payment.split.SplitEngine;

/**
 * Ponte única entre o meio de pagamento do catálogo e a faixa de taxa do motor de
 * divisão. A simulação do painel e a do checkout público chegam aqui, para que não
 * exista um caminho em que o vendedor veja uma taxa e o comprador pague outra.
 *
 * <p>O motor de divisão continua sendo a fonte do cálculo; isto só traduz o
 * parcelamento na faixa correspondente.
 */
public final class PriceMath {
    private PriceMath() { }

    /** Faixas do adquirente: à vista, até seis e até doze parcelas. */
    public static PaymentMethod providerMethod(OfferPaymentMethod method, int installments) {
        return switch (method) {
            case PIX -> PaymentMethod.PIX;
            case BOLETO -> PaymentMethod.BOLETO;
            case CARD -> installments <= 1 ? PaymentMethod.CARD_1
                    : installments <= 6 ? PaymentMethod.CARD_6
                    : PaymentMethod.CARD_12;
        };
    }

    public static Split split(long paidCents, OfferPaymentMethod method, int installments,
            Plan plan, int commissionBps) {
        return SplitEngine.split(paidCents, providerMethod(method, installments), plan, commissionBps);
    }
}
