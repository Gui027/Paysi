package com.paysi.payment.provider.asaas.dto;

/** Resposta de POST /v3/accounts. {@code walletId} é o identificador usado no split de pagamentos. */
public record AsaasAccountResponse(String id, String walletId) {
}
