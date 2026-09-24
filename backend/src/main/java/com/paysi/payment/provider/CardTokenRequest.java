package com.paysi.payment.provider;

/**
 * Dados brutos do cartão, usados só para trocar por um token no provedor. Vive apenas na
 * chamada de tokenização — nunca entra em {@link ProviderPaymentRequest}, que segue sem PAN/CVV.
 * {@code toString} não expõe nada sensível.
 */
public record CardTokenRequest(ProviderBuyer buyer, String holderName, String number, String expiryMonth,
                               String expiryYear, String ccv, String postalCode, String addressNumber,
                               String phone, String remoteIp) {
    @Override
    public String toString() {
        return "CardTokenRequest[card=[REDACTED], buyer=[REDACTED]]";
    }
}
