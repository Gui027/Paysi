package com.paysi.payment.provider.asaas.dto;

import java.util.List;

public record AsaasErrorResponse(List<AsaasError> errors) {
    public record AsaasError(String code, String description) {
    }

    public String firstCodeOr(String fallback) {
        if (errors == null || errors.isEmpty() || errors.get(0).code() == null) return fallback;
        return errors.get(0).code();
    }
}
