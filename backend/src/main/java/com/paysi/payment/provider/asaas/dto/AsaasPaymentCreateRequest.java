package com.paysi.payment.provider.asaas.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Corpo de POST /v3/payments. Campos nulos são omitidos (ver {@link JsonInclude}) porque a Asaas
 * usa {@code value}+{@code dueDate} para cobrança avulsa e {@code totalValue}+{@code installmentCount}
 * para parcelamento — os dois pares não podem ser enviados juntos.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AsaasPaymentCreateRequest(String customer, String billingType, BigDecimal value,
        BigDecimal totalValue, Integer installmentCount, LocalDate dueDate, String description,
        String externalReference, CreditCard creditCard, List<SplitItem> split) {

    public record CreditCard(String creditCardToken) {
    }

    public record SplitItem(String walletId, BigDecimal fixedValue) {
    }
}
