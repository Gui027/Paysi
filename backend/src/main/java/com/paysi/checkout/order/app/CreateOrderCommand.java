package com.paysi.checkout.order.app;

import com.paysi.catalog.offer.domain.OfferPaymentMethod;
import com.paysi.checkout.order.domain.BuyerAddress;
import com.paysi.identity.domain.PersonType;

/**
 * Submissão do checkout. O valor escolhido só existe para ofertas flexíveis; o servidor
 * relê a oferta e valida o mínimo. Descontos e taxas nunca vêm do navegador.
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
        String reference,
        String phone,
        String ip,
        Long amountCents
) {
    /** Identificador do cliente no sistema do vendedor (opcional, até 128 caracteres). */
    public static final int REFERENCE_MAX_LENGTH = 128;

    public CreateOrderCommand {
        if (phone != null) {
            phone = phone.replaceAll("\\D", "");
            if (phone.isEmpty()) phone = null;
            else if (phone.length() < 10 || phone.length() > 13) {
                throw new com.paysi.core.error.ValidationException("PHONE_INVALID",
                        "Informe o celular com DDD (10 a 13 dígitos)", "phone");
            }
        }
        if (ip != null && ip.length() > 64) ip = ip.substring(0, 64);
        if (reference != null) {
            reference = reference.strip();
            if (reference.isEmpty()) reference = null;
            else if (reference.length() > REFERENCE_MAX_LENGTH) {
                throw new com.paysi.core.error.ValidationException("REFERENCE_TOO_LONG",
                        "A referência deve ter no máximo 128 caracteres", "reference");
            }
        }
    }

    /** Mantém compatibilidade com chamadas anteriores à precificação variável. */
    public CreateOrderCommand(String name, String email, PersonType personType, String taxId, String legalName,
                              String municipalReg, BuyerAddress address, OfferPaymentMethod method,
                              int installments, String cardToken, String coupon, String visitorKey,
                              String termsHash, String reference, String phone, String ip) {
        this(name, email, personType, taxId, legalName, municipalReg, address, method, installments, cardToken,
                coupon, visitorKey, termsHash, reference, phone, ip, null);
    }

    /** Pedido com referência, sem celular nem IP. */
    public CreateOrderCommand(String name, String email, PersonType personType, String taxId, String legalName,
                              String municipalReg, BuyerAddress address, OfferPaymentMethod method,
                              int installments, String cardToken, String coupon, String visitorKey,
                              String termsHash, String reference) {
        this(name, email, personType, taxId, legalName, municipalReg, address, method, installments, cardToken,
                coupon, visitorKey, termsHash, reference, null, null, null);
    }

    /** Devolve o comando com o IP de origem (preenchido pelo controller, nunca pelo corpo). */
    public CreateOrderCommand withIp(String clientIp) {
        return new CreateOrderCommand(name, email, personType, taxId, legalName, municipalReg, address, method,
                installments, cardToken, coupon, visitorKey, termsHash, reference, phone, clientIp, amountCents);
    }

    /** Pedido sem referência externa; mantém a assinatura anterior. */
    public CreateOrderCommand(String name, String email, PersonType personType, String taxId, String legalName,
                              String municipalReg, BuyerAddress address, OfferPaymentMethod method,
                              int installments, String cardToken, String coupon, String visitorKey,
                              String termsHash) {
        this(name, email, personType, taxId, legalName, municipalReg, address, method, installments, cardToken,
                coupon, visitorKey, termsHash, null, null, null, null);
    }

    @Override
    public String toString() {
        return "CreateOrderCommand[method=" + method + ", installments=" + installments
                + ", coupon=" + coupon + ", personType=" + personType
                + ", buyer=[REDACTED], cardToken=[REDACTED], visitorKey=[REDACTED]]";
    }
}
