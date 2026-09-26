package com.paysi.sales.app;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Modelos de leitura da tela de Vendas do vendedor. Nenhum valor é calculado no navegador. */
public final class SalesModels {
    private SalesModels() { }

    public static final Set<String> CHARGE_STATUSES = Set.of(
            "PENDING", "PAID", "FAILED", "EXPIRED", "PARTIALLY_REFUNDED", "REFUNDED", "CHARGEBACK");
    public static final Set<String> APPROVED_STATUSES = Set.of("PAID", "PARTIALLY_REFUNDED");
    public static final Set<String> METHODS = Set.of("PIX", "CARD", "BOLETO");

    /** Filtros da lista; {@code approvedOnly} é a aba "Aprovadas" e ignora {@code statuses}. */
    public record SalesFilter(boolean approvedOnly, String query, Set<String> statuses, String method,
                              UUID productId, LocalDate from, LocalDate to) { }

    public record SaleRow(UUID id, String code, Instant createdAt, Instant approvedAt, String status,
                          String productName, UUID productId, String offerName, String method, int installments,
                          String buyerName, String buyerEmail, long netCents) { }

    public record SalesSummary(long count, long netCents) { }

    public record SalesPage(List<SaleRow> items, int page, int size, long total, int totalPages,
                            SalesSummary summary) { }

    public record Buyer(String name, String email, String phone, String taxId, String personType, String ip) { }

    public record Amounts(long basePriceCents, long discountCents, long paidCents, long feesCents,
                          long affiliateCents, long sellerCents, long refundedCents, long netCents) { }

    public record Participant(String name, String role, long amountCents) { }

    /** {@code sellerCents} é o líquido da venda que volta ao comprador (a coluna "Valor líquido" da tela). */
    public record RefundRow(UUID id, UUID chargeId, String saleCode, String productName, String buyerName,
                            String buyerEmail, String buyerPhone, long amountCents, long sellerCents,
                            String reason, String status, String requestedBy, Instant createdAt,
                            Instant settledAt) { }

    public record RefundFilter(String query, Set<String> statuses, Set<String> origins, LocalDate from,
                               LocalDate to) { }

    public record RefundsPage(List<RefundRow> items, int page, int size, long total, int totalPages) { }

    /** Estado do repasse ao vendedor: o que a tela mostra em "Seu recebimento". */
    public enum PayoutState { WAITING_PAYMENT, TO_RELEASE, RELEASED, REFUNDED, NOT_APPLICABLE }

    public record SaleDetail(UUID id, String code, String status, String type, String productName, UUID productId,
                             String offerName, String method, int installments, Instant createdAt,
                             Instant approvedAt, Instant availableAt, String reference, Integer cycleNumber,
                             UUID subscriptionId, String couponCode, Buyer buyer, Amounts amounts,
                             List<Participant> split, PayoutState payoutState, boolean canRefund,
                             List<RefundRow> refunds) { }
}
