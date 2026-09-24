package com.paysi.payment.provider.asaas.dto;

public record AsaasTokenizeRequest(String customer, CreditCard creditCard, HolderInfo creditCardHolderInfo,
                                   String remoteIp) {
    public record CreditCard(String holderName, String number, String expiryMonth, String expiryYear, String ccv) {
        @Override
        public String toString() {
            return "CreditCard[REDACTED]";
        }
    }

    public record HolderInfo(String name, String email, String cpfCnpj, String postalCode, String addressNumber,
                             String phone) {
    }

    @Override
    public String toString() {
        return "AsaasTokenizeRequest[customer=" + customer + ", card=[REDACTED]]";
    }
}
