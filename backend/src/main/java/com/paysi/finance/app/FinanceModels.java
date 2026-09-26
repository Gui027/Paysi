package com.paysi.finance.app;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Modelos de leitura da tela Financeiro do vendedor. Nenhuma conta com dinheiro é feita no navegador. */
public final class FinanceModels {
    private FinanceModels() { }

    public record Holder(String name, String personType, String taxId, String country) { }

    /** {@code pendingCents} reúne o que ainda está em garantia ou aguardando liberação. */
    public record Balance(long availableCents, long pendingCents, long reserveCents, long debtCents) { }

    public record PixAccount(UUID bankAccountId, String keyType, String key, Instant verifiedAt) { }

    public record Overview(Holder holder, Balance balance, PixAccount pix, String kycStatus, boolean mfaEnabled,
                           long payoutFeeCents, long minPayoutCents, long mfaThresholdCents) { }

    public record PayoutRow(UUID id, Instant createdAt, long amountCents, String status, String destinationName,
                            String pixKeyType, String pixKey, String receiptUrl) { }

    public record PayoutsPage(List<PayoutRow> items, int page, int size, long total, int totalPages) { }

    public record MethodFee(String method, int feeBps, long fixedCents) { }

    /** Taxas do plano atual, prazo de recebimento da conta e a reserva de segurança retida em cada venda. */
    public record Fees(String plan, List<MethodFee> methods, int payoutDelayDays, int reserveBps, int reserveDays) { }

    public record AccountRow(String fullName, String personType, String taxId, String kycStatus, String payoutDelay) { }

    public record BankRow(UUID id, String holderName, String pixKeyType, byte[] pixKeyEncrypted, Instant verifiedAt) { }

    public record PayoutRaw(UUID id, Instant createdAt, long amountCents, String status, String holderName,
                            String pixKeyType, byte[] pixKeyEncrypted, String receiptUrl) { }
}
