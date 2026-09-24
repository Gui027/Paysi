package com.paysi.payment.provider.asaas;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paysi.payment.provider.asaas.dto.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Supplier;

/**
 * Chamadas cruas à API da Asaas (v3). Não conhece o domínio do Paysi — isso é
 * responsabilidade do {@link AsaasMapper} e do {@link AsaasPaymentProvider}.
 * Condicional junto com {@link AsaasConfiguration}: sem isso o Spring tentaria
 * injetar o bean "asaasRestTemplate", que só existe com {@code paysi.provider=asaas}.
 */
@Component
@ConditionalOnProperty(name = "paysi.provider", havingValue = "asaas")
class AsaasClient {
    private final RestTemplate http;
    private final ObjectMapper json;

    AsaasClient(@Qualifier("asaasRestTemplate") RestTemplate asaasRestTemplate, ObjectMapper json) {
        this.http = asaasRestTemplate;
        this.json = json;
    }

    AsaasCustomerResponse findOrCreateCustomer(String name, String email, String cpfCnpj) {
        var existing = execute(() -> http.exchange("/customers?cpfCnpj={cpfCnpj}", HttpMethod.GET,
                HttpEntity.EMPTY, new ParameterizedTypeReference<AsaasListResponse<AsaasCustomerResponse>>() {},
                cpfCnpj).getBody());
        List<AsaasCustomerResponse> found = existing == null ? List.of() : existing.dataOrEmpty();
        if (!found.isEmpty()) return found.get(0);
        return execute(() -> http.postForObject("/customers",
                new AsaasCustomerRequest(name, email, cpfCnpj), AsaasCustomerResponse.class));
    }

    AsaasPaymentResponse createPayment(AsaasPaymentCreateRequest request) {
        return execute(() -> http.postForObject("/payments", request, AsaasPaymentResponse.class));
    }

    AsaasPaymentResponse getPayment(String paymentId) {
        return execute(() -> http.getForObject("/payments/{id}", AsaasPaymentResponse.class, paymentId));
    }

    AsaasPixQrCodeResponse getPixQrCode(String paymentId) {
        return execute(() -> http.getForObject("/payments/{id}/pixQrCode",
                AsaasPixQrCodeResponse.class, paymentId));
    }

    AsaasPaymentResponse refund(String paymentId, BigDecimal amount) {
        var body = amount == null ? java.util.Map.of() : java.util.Map.of("value", amount);
        return execute(() -> http.postForObject("/payments/{id}/refund", body,
                AsaasPaymentResponse.class, paymentId));
    }

    private <T> T execute(Supplier<T> call) {
        try {
            return call.get();
        } catch (HttpStatusCodeException error) {
            String body = error.getResponseBodyAsString();
            String code = parseErrorCode(body);
            boolean serverError = error.getStatusCode().is5xxServerError();
            throw new AsaasApiException(code, "Asaas respondeu " + error.getStatusCode() + ": " + body,
                    serverError, error);
        } catch (ResourceAccessException error) {
            throw new AsaasApiException("PROVIDER_TIMEOUT", "Falha de rede ao chamar a Asaas", true, error);
        } catch (RestClientException error) {
            // Resposta 2xx que não deu pra ler (ex.: formato de campo inesperado): erro controlado, não 500.
            throw new AsaasApiException("PROVIDER_BAD_RESPONSE", "Resposta inesperada da Asaas: " + error.getMessage(),
                    true, error);
        }
    }

    private String parseErrorCode(String body) {
        try {
            return json.readValue(body, AsaasErrorResponse.class).firstCodeOr("PROVIDER_ERROR");
        } catch (Exception parseFailure) {
            return "PROVIDER_ERROR";
        }
    }
}
