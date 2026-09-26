package com.paysi.sales.app;

import com.paysi.sales.app.SalesModels.Buyer;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Modelos de leitura da tela de Assinaturas do vendedor. */
public final class SubscriptionsModels {
    private SubscriptionsModels() { }

    public static final Set<String> STATUSES = Set.of("TRIAL", "ACTIVE", "PAST_DUE", "CANCELED");
    public static final Set<String> CYCLES = Set.of("MONTHLY", "QUARTERLY", "SEMIANNUAL", "ANNUAL");

    /** {@code tab}: {@code active} (não canceladas), {@code canceled} ou {@code all}. */
    public record SubscriptionsFilter(String tab, String query, Set<String> statuses, String cycle, String method,
                                      UUID productId, LocalDate from, LocalDate to) { }

    /** {@code netCents} é o líquido do vendedor na cobrança mais recente; nulo enquanto não houve cobrança. */
    public record SubscriptionRow(UUID id, String code, Instant createdAt, String status, boolean cancelPending,
                                  String productName, UUID productId, String offerName, String cycle,
                                  String buyerName, String buyerEmail, Long netCents, Instant nextChargeAt) { }

    /** Assinaturas ativas (em teste ou pagas) e o faturamento recorrente mensal líquido das pagas. */
    public record SubscriptionsSummary(long activeCount, long monthlyRecurringCents) { }

    public record SubscriptionsPage(List<SubscriptionRow> items, int page, int size, long total, int totalPages,
                                    SubscriptionsSummary summary) { }

    public record SubscriptionPayment(UUID chargeId, int cycleNumber, Instant createdAt, Instant paidAt,
                                      String status, long netCents) { }

    public record SubscriptionDetail(UUID id, String code, String status, boolean cancelPending, String type,
                                     Instant createdAt, Instant accessUntil, Instant trialEndsAt,
                                     Instant nextChargeAt, Instant canceledAt, String productName, UUID productId,
                                     String offerName, String cycle, Long netCents, int installments, String method,
                                     int approvedCharges, Buyer buyer, List<SubscriptionPayment> payments,
                                     boolean canCancel) { }
}
