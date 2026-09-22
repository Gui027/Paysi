package com.paysi.payment.provider.asaas;

import com.paysi.payment.provider.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Adaptador real da Asaas para {@link PaymentProvider}. Duas lacunas conhecidas,
 * deixadas explícitas em vez de escondidas atrás de uma implementação "completa":
 *
 * <ol>
 *   <li><b>Split</b>: não é enviado à Asaas ainda (ver {@link AsaasMapper}) — falta
 *       modelar {@code walletId} por subconta no domínio.</li>
 *   <li><b>3DS</b>: a Asaas não tem um endpoint de "confirmar desafio com token" como
 *       este contrato assume (herdado do {@code FakePaymentProvider}). Na Asaas, o
 *       comprador completa o desafio na {@code threeDSecureChallengeUrl} pelo navegador
 *       e o resultado chega depois por webhook. {@link #confirmThreeDs} aqui faz o
 *       melhor possível: relê o status atual da cobrança na Asaas.</li>
 * </ol>
 */
@Component
@ConditionalOnProperty(name = "paysi.provider", havingValue = "asaas")
public class AsaasPaymentProvider implements PaymentProvider {
    private static final Logger log = LoggerFactory.getLogger(AsaasPaymentProvider.class);

    private final AsaasClient client;

    AsaasPaymentProvider(AsaasClient client) {
        this.client = client;
    }

    @Override
    public ProviderPaymentResult charge(ProviderPaymentRequest request) {
        try {
            var buyer = request.buyer();
            var customer = client.findOrCreateCustomer(buyer.name(), buyer.email(), buyer.taxId());
            var response = client.createPayment(AsaasMapper.toCreateRequest(request, customer.id()));

            String pixPayload = null;
            if (request.method() == ProviderPaymentMethod.PIX) {
                pixPayload = fetchPixPayload(response.id());
            }
            return AsaasMapper.toChargeResult(response, request.method(), request.installments(), pixPayload);
        } catch (AsaasApiException error) {
            log.warn("Cobrança recusada/falhou na Asaas para orderId={}: {}", request.orderId(), error.getMessage());
            return AsaasMapper.errorResult(request.orderId(), error);
        }
    }

    @Override
    public ProviderPaymentResult confirmThreeDs(ProviderThreeDsConfirmation confirmation) {
        try {
            var response = client.getPayment(confirmation.providerChargeId());
            return AsaasMapper.toChargeResult(response, ProviderPaymentMethod.CARD, 1, null);
        } catch (AsaasApiException error) {
            log.warn("Falha ao consultar status 3DS na Asaas para orderId={}: {}",
                    confirmation.orderId(), error.getMessage());
            return AsaasMapper.errorResult(confirmation.orderId(), error);
        }
    }

    @Override
    public ProviderRefundResult refund(ProviderRefundRequest request) {
        try {
            var amount = request.amountCents() > 0 ? AsaasMapper.toReais(request.amountCents()) : null;
            var response = client.refund(request.providerChargeId(), amount);
            return new ProviderRefundResult(response.id(), true, null);
        } catch (AsaasApiException error) {
            log.warn("Reembolso recusado/falhou na Asaas para refundId={}: {}",
                    request.refundId(), error.getMessage());
            return new ProviderRefundResult(null, false,
                    error.errorCode() == null ? "PROVIDER_REFUND_UNAVAILABLE" : error.errorCode());
        }
    }

    private String fetchPixPayload(String paymentId) {
        try {
            return client.getPixQrCode(paymentId).payload();
        } catch (AsaasApiException error) {
            log.warn("Cobrança Pix criada (paymentId={}) mas QR Code não pôde ser obtido: {}",
                    paymentId, error.getMessage());
            return null;
        }
    }
}
