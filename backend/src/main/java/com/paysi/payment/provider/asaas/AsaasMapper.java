package com.paysi.payment.provider.asaas;

import com.paysi.payment.provider.*;
import com.paysi.payment.provider.asaas.dto.AsaasPaymentCreateRequest;
import com.paysi.payment.provider.asaas.dto.AsaasPaymentResponse;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Tradução pura entre o domínio de pagamento do Paysi e o formato da API da Asaas.
 * Sem Spring, sem HTTP — só conversão, para poder ser testado em isolamento.
 *
 * <p><b>Split real ainda não é enviado à Asaas.</b> {@link ProviderSplit} só carrega
 * valores em centavos; a Asaas precisa de {@code walletId} por recebedor (subconta),
 * conceito que o domínio do Paysi ainda não modela. Até isso existir, o valor cheio
 * é cobrado na conta mestre e o rateio permanece só na contabilidade interna
 * ({@code payment.split}) — nenhum valor é de fato roteado para subcontas na Asaas.
 */
final class AsaasMapper {
    private AsaasMapper() {
    }

    static final int DEFAULT_DUE_DAYS_PIX_OR_CARD = 1;

    static AsaasPaymentCreateRequest toCreateRequest(ProviderPaymentRequest request, String customerId) {
        String billingType = switch (request.method()) {
            case PIX -> "PIX";
            case BOLETO -> "BOLETO";
            case CARD -> "CREDIT_CARD";
        };
        LocalDate dueDate = LocalDate.now(ZoneOffset.UTC).plusDays(
                request.method() == ProviderPaymentMethod.BOLETO ? request.boletoDueDays() : DEFAULT_DUE_DAYS_PIX_OR_CARD);

        String creditCardToken = request.method() == ProviderPaymentMethod.CARD ? request.paymentToken() : null;

        boolean installment = request.installments() > 1;
        BigDecimal amount = toReais(request.amountCents());

        return new AsaasPaymentCreateRequest(customerId, billingType,
                installment ? null : amount,
                installment ? amount : null,
                installment ? request.installments() : null,
                dueDate, "Pedido " + request.orderId(), request.orderId().toString(),
                creditCardToken, null);
    }

    static ProviderPaymentResult toChargeResult(AsaasPaymentResponse response, ProviderPaymentMethod method,
            int installments, String pixPayload) {
        var status = toStatus(response.status());
        var threeDs = toThreeDs(method, status, response.threeDSecureChallengeUrl());
        var data = toPaymentData(method, response, pixPayload);
        // Parcelamento (installments > 1): a Asaas devolve um grupo de parcelas, não uma
        // cobrança só, e cada parcela tem seu próprio providerId/vencimento — isso exige uma
        // chamada extra a GET /v3/installments/{id}/payments que ainda não foi implementada.
        // Melhor devolver vazio aqui do que inventar datas que depois batem errado no
        // ReceivableScheduleService (RECEIVABLE_SCHEDULE_MISMATCH).
        var receivables = installments == 1 && response.value() != null
                ? List.of(new ProviderReceivable(1, response.id(), toInstant(response.dueDate()), toCents(response.value())))
                : List.<ProviderReceivable>of();
        return new ProviderPaymentResult(response.id(), status, data, 0L, receivables, threeDs, null, false);
    }

    static ProviderPaymentResult errorResult(java.util.UUID orderId, AsaasApiException error) {
        String code = error.errorCode() == null ? "PROVIDER_ERROR" : error.errorCode();
        return new ProviderPaymentResult("asaas_failed_" + orderId,
                error.serverError() ? ProviderChargeStatus.ERROR : ProviderChargeStatus.DECLINED,
                null, 0L, List.of(), new ProviderThreeDs("NOT_APPLICABLE", null, null),
                code, error.serverError());
    }

    static ProviderChargeStatus toStatus(String asaasStatus) {
        if (asaasStatus == null) return ProviderChargeStatus.ERROR;
        return switch (asaasStatus) {
            case "CONFIRMED", "RECEIVED", "RECEIVED_IN_CASH" -> ProviderChargeStatus.APPROVED;
            case "PENDING", "AWAITING_RISK_ANALYSIS" -> ProviderChargeStatus.PENDING;
            case "OVERDUE" -> ProviderChargeStatus.EXPIRED;
            default -> ProviderChargeStatus.PENDING;
        };
    }

    static BigDecimal toReais(long cents) {
        return BigDecimal.valueOf(cents, 2);
    }

    static long toCents(BigDecimal reais) {
        return reais.movePointRight(2).longValueExact();
    }

    private static Instant toInstant(LocalDate date) {
        return date == null ? null : date.atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    private static ProviderThreeDs toThreeDs(ProviderPaymentMethod method, ProviderChargeStatus status,
            String challengeUrl) {
        if (method != ProviderPaymentMethod.CARD) return new ProviderThreeDs("NOT_APPLICABLE", null, null);
        if (challengeUrl != null && !challengeUrl.isBlank()) {
            return new ProviderThreeDs("CHALLENGE_REQUIRED", challengeUrl, null);
        }
        return switch (status) {
            case APPROVED -> new ProviderThreeDs("AUTHENTICATED", null, null);
            case DECLINED -> new ProviderThreeDs("FAILED", null, null);
            default -> new ProviderThreeDs("NOT_APPLICABLE", null, null);
        };
    }

    private static ProviderPaymentData toPaymentData(ProviderPaymentMethod method, AsaasPaymentResponse response,
            String pixPayload) {
        return switch (method) {
            case PIX -> new ProviderPaymentData(pixPayload, null, null, toInstant(response.dueDate()));
            case BOLETO -> new ProviderPaymentData(null, response.identificationField(), response.bankSlipUrl(),
                    toInstant(response.dueDate()));
            case CARD -> null;
        };
    }
}
