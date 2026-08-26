package com.paysi.config;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Formato de erro da API (documento 2, §4.1): {@code code} estável, {@code message}
 * legível e {@code field} quando o erro é de um único campo. {@code fieldErrors}
 * cobre o caso de validação com mais de um campo inválido ao mesmo tempo.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(String code, String message, String field, List<FieldError> fieldErrors) {

    public static ApiError of(String code, String message, String field) {
        return new ApiError(code, message, field, null);
    }

    public static ApiError of(String code, String message, List<FieldError> fieldErrors) {
        return new ApiError(code, message, null, fieldErrors);
    }

    public record FieldError(String field, String code, String message) {
    }
}
