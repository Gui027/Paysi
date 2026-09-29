package com.paysi.payment.provider.asaas.dto;

/**
 * Resposta de POST /v3/accounts. {@code walletId} é o identificador usado no split de pagamentos;
 * {@code apiKey} é a chave própria da subconta — a Asaas só devolve isso uma vez, nunca mais depois.
 */
public record AsaasAccountResponse(String id, String walletId, String apiKey) {
}
