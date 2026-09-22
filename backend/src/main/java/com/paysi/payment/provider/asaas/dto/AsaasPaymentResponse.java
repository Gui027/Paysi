package com.paysi.payment.provider.asaas.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record AsaasPaymentResponse(String id, String status, BigDecimal value, BigDecimal netValue,
        String invoiceUrl, String bankSlipUrl, String identificationField, LocalDate dueDate,
        String threeDSecureChallengeUrl) {
}
