package com.paysi.payment.provider.asaas.dto;

/**
 * Um grupo de documento de GET /v3/myAccount/documents. {@code onboardingUrl} presente = esse
 * documento só pode ser enviado por aquele link externo da Asaas (não aceita POST via API); ausente =
 * enviar por POST /v3/myAccount/documents/{id} direto pela Paysi.
 */
public record AsaasDocumentGroup(String id, String status, String type, String description, String onboardingUrl) {
}
