package com.paysi.payment.provider.asaas.dto;

public record AsaasTokenizeResponse(String creditCardToken, String creditCardBrand, String creditCardNumber) {
}
