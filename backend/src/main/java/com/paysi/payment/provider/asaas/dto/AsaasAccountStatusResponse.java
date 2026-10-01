package com.paysi.payment.provider.asaas.dto;

/** Resposta de GET /v3/myAccount/status para uma subconta. */
public record AsaasAccountStatusResponse(String id, String commercialInfo, String bankAccountInfo,
                                         String documentation, String general) {
}
