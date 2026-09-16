package com.paysi.checkout.order.web.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.paysi.catalog.offer.domain.OfferPaymentMethod;
import com.paysi.checkout.order.app.CreateOrderCommand;
import com.paysi.checkout.order.domain.BuyerAddress;
import com.paysi.core.error.ValidationException;
import com.paysi.identity.domain.PersonType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * Corpo do pedido. Não existe campo de valor aqui, e um corpo que tente mandar
 * {@code amountCents} ou {@code price} é <em>recusado</em>, não silenciosamente
 * ignorado: aceitar e descartar deixaria o integrador achar que o preço dele valeu.
 *
 * <p>A recusa é explícita porque a aplicação roda com
 * {@code FAIL_ON_UNKNOWN_PROPERTIES} desligado — confiar no padrão global do Jackson
 * faria esta garantia depender de configuração alheia ao contrato.
 */
public record CreateOrderRequest(
        @NotNull @Valid BuyerRequest buyer,
        @NotNull OfferPaymentMethod method,
        Integer installments,
        @Size(max = 512) String cardToken,
        @Size(max = 32) String coupon,
        @Size(max = 128) String visitorKey,
        @NotNull @Size(min = 8, max = 128) String termsHash,
        @JsonAnySetter Map<String, Object> unknown
) {
    public CreateOrderRequest {
        rejectUnknown(unknown);
    }

    public CreateOrderCommand toCommand() {
        return new CreateOrderCommand(buyer.name(), buyer.email(), buyer.personType(),
                buyer.taxId(), buyer.legalName(), buyer.municipalReg(), buyer.address(),
                method, installments == null ? 1 : installments, cardToken, coupon,
                visitorKey, termsHash);
    }

    public record BuyerRequest(
            @NotNull @Size(max = 200) String name,
            @NotNull @Size(max = 320) String email,
            @NotNull PersonType personType,
            @NotNull @Size(max = 32) String taxId,
            @Size(max = 200) String legalName,
            @Size(max = 32) String municipalReg,
            @Valid BuyerAddress address,
            @JsonAnySetter Map<String, Object> unknown
    ) {
        public BuyerRequest {
            rejectUnknown(unknown);
        }

        @Override
        public String toString() {
            return "BuyerRequest[personType=" + personType + ", dados=[REDACTED]]";
        }
    }

    static void rejectUnknown(Map<String, Object> unknown) {
        if (unknown == null || unknown.isEmpty()) return;
        String field = unknown.keySet().iterator().next();
        throw new ValidationException("UNKNOWN_FIELD",
                "O campo '" + field + "' não faz parte do pedido. Preço e desconto são"
                        + " calculados pelo servidor.", field);
    }

    @Override
    public String toString() {
        return "CreateOrderRequest[method=" + method + ", installments=" + installments
                + ", coupon=" + coupon + ", buyer=[REDACTED], cardToken=[REDACTED],"
                + " visitorKey=[REDACTED]]";
    }
}
