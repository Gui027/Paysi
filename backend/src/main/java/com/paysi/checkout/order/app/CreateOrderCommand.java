package com.paysi.checkout.order.app;

import com.paysi.catalog.offer.domain.OfferPaymentMethod;
import com.paysi.checkout.order.domain.BuyerAddress;
import com.paysi.identity.domain.PersonType;

/**
 * Submissão do checkout. Repare no que não existe aqui: nenhum campo de preço,
 * desconto ou taxa. O valor é sempre relido do banco (documento 5, passo 5), então
 * não há o que o navegador possa forjar.
 *
 * <p>O {@code toString} é mascarado: o comando carrega PII e token de cartão, e
 * nenhum dos dois pode vazar em log (documento 3, §4).
 */
public record CreateOrderCommand(
        String name,
        String email,
        PersonType personType,
        String taxId,
        String legalName,
        String municipalReg,
        BuyerAddress address,
        OfferPaymentMethod method,
        int installments,
        String cardToken,
        String coupon,
        String visitorKey,
        String termsHash,
        String reference
) {
    /** Identificador do cliente no sistema do vendedor (opcional, até 128 caracteres). */
    public static final int REFERENCE_MAX_LENGTH = 128;

    public CreateOrderCommand {
        if (reference != null) {
            reference = reference.strip();
            if (reference.isEmpty()) reference = null;
            else if (reference.length() > REFERENCE_MAX_LENGTH) {
                throw new com.paysi.core.error.ValidationException("REFERENCE_TOO_LONG",
                        "A referência deve ter no máximo 128 caracteres", "reference");
            }
        }
    }

    /** Pedido sem referência externa; mantém a assinatura anterior. */
    public CreateOrderCommand(String name, String email, PersonType personType, String taxId, String legalName,
                              String municipalReg, BuyerAddress address, OfferPaymentMethod method,
                              int installments, String cardToken, String coupon, String visitorKey,
                              String termsHash) {
        this(name, email, personType, taxId, legalName, municipalReg, address, method, installments, cardToken,
                coupon, visitorKey, termsHash, null);
    }

    @Override
    public String toString() {
        return "CreateOrderCommand[method=" + method + ", installments=" + installments
                + ", coupon=" + coupon + ", personType=" + personType
                + ", buyer=[REDACTED], cardToken=[REDACTED], visitorKey=[REDACTED]]";
    }
}
