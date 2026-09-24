package com.paysi.payment.provider.asaas;

import com.paysi.payment.provider.CardTokenRequest;
import com.paysi.payment.provider.CardTokenResult;
import com.paysi.payment.provider.CardTokenizationException;
import com.paysi.payment.provider.CardTokenizer;
import com.paysi.payment.provider.asaas.dto.AsaasTokenizeRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Troca os dados do cartão por um token da Asaas. O token fica vinculado ao cliente Asaas do
 * comprador (o mesmo que {@link AsaasPaymentProvider#charge} reutiliza pelo CPF/CNPJ), então só
 * serve para cobranças desse comprador. Nada do cartão é logado nem guardado aqui.
 */
@Component
@ConditionalOnProperty(name = "paysi.provider", havingValue = "asaas")
public class AsaasCardTokenizer implements CardTokenizer {
    private static final Logger log = LoggerFactory.getLogger(AsaasCardTokenizer.class);

    private final AsaasClient client;

    AsaasCardTokenizer(AsaasClient client) {
        this.client = client;
    }

    @Override
    public CardTokenResult tokenize(CardTokenRequest request) {
        try {
            var buyer = request.buyer();
            var customer = client.findOrCreateCustomer(buyer.name(), buyer.email(), buyer.taxId());
            var response = client.tokenizeCard(new AsaasTokenizeRequest(customer.id(),
                    new AsaasTokenizeRequest.CreditCard(request.holderName(), request.number(),
                            request.expiryMonth(), request.expiryYear(), request.ccv()),
                    new AsaasTokenizeRequest.HolderInfo(buyer.name(), buyer.email(), buyer.taxId(),
                            request.postalCode(), request.addressNumber(), request.phone()),
                    request.remoteIp()));
            return new CardTokenResult(response.creditCardToken(), response.creditCardBrand(),
                    response.creditCardNumber());
        } catch (AsaasApiException error) {
            // A mensagem da exceção carrega o corpo de erro da Asaas, que não repete dados do cartão.
            log.warn("Tokenização de cartão recusada pela Asaas: {}", error.errorCode());
            throw new CardTokenizationException(error.errorCode(),
                    error.serverError() ? "Provedor de pagamento indisponível" : "Cartão recusado ou dados inválidos",
                    error.serverError());
        }
    }
}
