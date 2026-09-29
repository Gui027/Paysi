package com.paysi.payment.provider.asaas;

import com.paysi.payment.provider.*;
import com.paysi.payment.provider.asaas.dto.AsaasPaymentCreateRequest;
import com.paysi.payment.provider.asaas.dto.AsaasPaymentResponse;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * Tradução pura entre o domínio de pagamento do Paysi e o formato da API da Asaas.
 * Sem Spring, sem HTTP — só conversão, para poder ser testado em isolamento.
 *
 * <p><b>Split real:</b> {@link ProviderSplit} carrega o {@code walletId} da subconta do vendedor e do
 * afiliado (quando existem). {@link #toSplit} monta a lista {@code split} da Asaas só com as fatias que
 * têm subconta cadastrada; a fatia da plataforma nunca entra na lista (ela é o que sobra na conta mestre
 * depois do split, comportamento padrão da Asaas). Sem nenhuma subconta, a lista fica vazia/nula e o valor
 * cheio cai na conta mestre — mesmo comportamento de antes desta divisão existir.</p>
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
                creditCardToken, toSplit(request.split()));
    }

    /** Só entra na lista quem tem subconta (walletId) e valor positivo; sem isso a Asaas não faz split. */
    static List<AsaasPaymentCreateRequest.SplitItem> toSplit(ProviderSplit split) {
        List<AsaasPaymentCreateRequest.SplitItem> items = new ArrayList<>();
        if (split.sellerWalletId() != null && !split.sellerWalletId().isBlank() && split.sellerCents() > 0) {
            items.add(new AsaasPaymentCreateRequest.SplitItem(split.sellerWalletId(), toReais(split.sellerCents())));
        }
        if (split.affiliateWalletId() != null && !split.affiliateWalletId().isBlank() && split.affiliateCents() > 0) {
            items.add(new AsaasPaymentCreateRequest.SplitItem(split.affiliateWalletId(), toReais(split.affiliateCents())));
        }
        return items.isEmpty() ? null : items;
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
