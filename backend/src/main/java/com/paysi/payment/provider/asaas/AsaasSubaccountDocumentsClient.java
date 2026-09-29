package com.paysi.payment.provider.asaas;

import com.paysi.payment.provider.asaas.dto.AsaasDocumentGroup;
import com.paysi.payment.provider.asaas.dto.AsaasListResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpStatusCodeException;

import java.util.List;
import java.util.function.Supplier;

/**
 * Documentos de verificação da subconta ({@code GET/POST /v3/myAccount/documents}). Diferente do resto
 * da integração: aqui a autenticação é com a chave de API da PRÓPRIA subconta (uma por vendedor), não a
 * chave mestre da Paysi — por isso usa o {@link RestTemplate} genérico da aplicação (sem o header fixo
 * do {@code asaasRestTemplate}) e monta o header a cada chamada.
 */
@Component
@ConditionalOnProperty(name = "paysi.provider", havingValue = "asaas")
class AsaasSubaccountDocumentsClient {
    private final RestTemplate http;
    private final String baseUrl;

    AsaasSubaccountDocumentsClient(RestTemplate http, @Value("${paysi.asaas.base-url}") String baseUrl) {
        this.http = http;
        this.baseUrl = baseUrl.replaceAll("/$", "");
    }

    List<AsaasDocumentGroup> listPendingDocuments(String subaccountApiKey) {
        return execute(() -> {
            var response = http.exchange(baseUrl + "/myAccount/documents", HttpMethod.GET,
                    new HttpEntity<>(headers(subaccountApiKey)),
                    new ParameterizedTypeReference<AsaasListResponse<AsaasDocumentGroup>>() { });
            return response.getBody() == null ? List.<AsaasDocumentGroup>of() : response.getBody().dataOrEmpty();
        });
    }

    void submitDocument(String subaccountApiKey, String documentGroupId, byte[] file, String filename, String contentType) {
        execute(() -> {
            HttpHeaders headers = headers(subaccountApiKey);
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            HttpHeaders partHeaders = new HttpHeaders();
            partHeaders.setContentType(MediaType.parseMediaType(contentType == null ? "application/octet-stream" : contentType));
            partHeaders.setContentDispositionFormData("documentFile", filename);
            HttpEntity<byte[]> part = new HttpEntity<>(file, partHeaders);
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("documentFile", part);
            http.postForObject(baseUrl + "/myAccount/documents/" + documentGroupId, new HttpEntity<>(body, headers), String.class);
            return null;
        });
    }

    private static HttpHeaders headers(String subaccountApiKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("access_token", subaccountApiKey);
        headers.set("User-Agent", "Paysi-Backend");
        return headers;
    }

    private <T> T execute(Supplier<T> call) {
        try {
            return call.get();
        } catch (HttpStatusCodeException error) {
            throw new AsaasApiException("PROVIDER_ERROR", "Asaas respondeu " + error.getStatusCode() + ": " + error.getResponseBodyAsString(),
                    error.getStatusCode().is5xxServerError(), error);
        } catch (ResourceAccessException error) {
            throw new AsaasApiException("PROVIDER_TIMEOUT", "Falha de rede ao chamar a Asaas", true, error);
        } catch (RestClientException error) {
            throw new AsaasApiException("PROVIDER_BAD_RESPONSE", "Resposta inesperada da Asaas: " + error.getMessage(), true, error);
        }
    }
}
