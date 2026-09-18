package com.paysi.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.slf4j.MDC;

import java.util.List;

/**
 * Formato de erro da API (documento 2, §4.1): {@code code} estável, {@code message}
 * legível, {@code field} quando o erro é de um único campo e {@code correlationId}
 * para amarrar a resposta ao rastro de log do mesmo request (RNF-028). {@code
 * fieldErrors} cobre o caso de validação com mais de um campo inválido ao mesmo tempo.
 *
 * <p>O {@code correlationId} é lido do MDC no momento da construção, não passado
 * explicitamente por cada handler: {@link com.paysi.config.CorrelationIdFilter} já o
 * colocou lá antes de a requisição chegar ao controller, então toda resposta de erro
 * carrega o mesmo identificador que aparece nos logs daquele request.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(String code, String message, String field, List<FieldError> fieldErrors,
        String correlationId) {

    public static ApiError of(String code, String message, String field) {
        return new ApiError(code, message, field, null, currentCorrelationId());
    }

    public static ApiError of(String code, String message, List<FieldError> fieldErrors) {
        return new ApiError(code, message, null, fieldErrors, currentCorrelationId());
    }

    private static String currentCorrelationId() {
        return MDC.get(CorrelationIdFilter.MDC_KEY);
    }

    public record FieldError(String field, String code, String message) {
    }
}
