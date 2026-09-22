package com.paysi.payment.provider.asaas;

/** Erro reportado pela Asaas (corpo {@code errors[]}) ou falha de transporte na chamada. */
class AsaasApiException extends RuntimeException {
    private final String errorCode;
    private final boolean serverError;

    AsaasApiException(String errorCode, String message, boolean serverError, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.serverError = serverError;
    }

    String errorCode() {
        return errorCode;
    }

    /** {@code true} para 5xx/timeout/IO (vale retentar); {@code false} para 4xx (recusa definitiva). */
    boolean serverError() {
        return serverError;
    }
}
