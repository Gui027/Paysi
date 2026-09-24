package com.paysi.payment.provider.asaas.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Corpo de POST /v3/payments. Campos nulos são omitidos (ver {@link JsonInclude}) porque a Asaas
 * usa {@code value}+{@code dueDate} para cobrança avulsa e {@code totalValue}+{@code installmentCount}
 * para parcelamento — os dois pares não podem ser enviados juntos. O token do cartão vai em
 * {@code creditCardToken} no nível de cima: aninhado em {@code creditCard} a Asaas ignora o token
 * e a cobrança fica PENDING sem cobrar o cartão (verificado no sandbox).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AsaasPaymentCreateRequest(String customer, String billingType, BigDecimal value,
        BigDecimal totalValue, Integer installmentCount, LocalDate dueDate, String description,
        String externalReference, String creditCardToken, List<SplitItem> split) {

    public record SplitItem(String walletId, BigDecimal fixedValue) {
    }
}
