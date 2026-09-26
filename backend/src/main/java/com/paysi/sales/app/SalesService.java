package com.paysi.sales.app;

import com.paysi.core.error.NotFoundException;
import com.paysi.core.error.ValidationException;
import com.paysi.sales.app.SalesModels.PayoutState;
import com.paysi.sales.app.SalesModels.RefundFilter;
import com.paysi.sales.app.SalesModels.RefundRow;
import com.paysi.sales.app.SalesModels.RefundsPage;
import com.paysi.sales.app.SalesModels.SaleDetail;
import com.paysi.sales.app.SalesModels.SaleRow;
import com.paysi.sales.app.SalesModels.SalesFilter;
import com.paysi.sales.app.SalesModels.SalesPage;
import com.paysi.sales.port.SalesQueryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class SalesService {
    public static final int DEFAULT_PAGE_SIZE = 10;
    public static final int MAX_PAGE_SIZE = 50;
    public static final int MAX_EXPORT_ROWS = 10_000;
    private static final Set<String> REFUND_STATUSES = Set.of("PENDING", "SUCCEEDED", "FAILED");
    private static final Set<String> REFUND_ORIGINS = Set.of("BUYER", "SELLER", "ADMIN", "SYSTEM");

    private final SalesQueryRepository repository;
    private final Clock clock;

    @Autowired
    public SalesService(SalesQueryRepository repository) {
        this(repository, Clock.systemUTC());
    }

    SalesService(SalesQueryRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /** Monta o filtro validando cada texto recebido da URL: nada chega ao banco sem passar por aqui. */
    public SalesFilter filter(String tab, String query, List<String> statuses, String method, UUID productId,
                              String from, String to) {
        boolean approvedOnly = !"all".equalsIgnoreCase(tab);
        Set<String> parsedStatuses = new LinkedHashSet<>();
        for (String status : statuses == null ? List.<String>of() : statuses) {
            String normalized = status == null ? "" : status.strip().toUpperCase();
            if (normalized.isEmpty()) continue;
            if (!SalesModels.CHARGE_STATUSES.contains(normalized)) {
                throw new ValidationException("SALES_STATUS_INVALID", "Status de venda inválido", "status");
            }
            parsedStatuses.add(normalized);
        }
        String normalizedMethod = method == null || method.isBlank() ? null : method.strip().toUpperCase();
        if (normalizedMethod != null && !SalesModels.METHODS.contains(normalizedMethod)) {
            throw new ValidationException("SALES_METHOD_INVALID", "Meio de pagamento inválido", "method");
        }
        LocalDate start = date(from, "from");
        LocalDate end = date(to, "to");
        if (start != null && end != null && end.isBefore(start)) {
            throw new ValidationException("SALES_PERIOD_INVALID", "A data final deve ser igual ou posterior à inicial", "to");
        }
        String text = query == null || query.isBlank() ? null : query.strip();
        if (text != null && text.length() > 120) {
            throw new ValidationException("SALES_QUERY_TOO_LONG", "A busca deve ter no máximo 120 caracteres", "q");
        }
        return new SalesFilter(approvedOnly, text, parsedStatuses, normalizedMethod, productId, start, end);
    }

    @Transactional(readOnly = true)
    public SalesPage list(UUID sellerId, SalesFilter filter, Integer requestedPage, Integer requestedSize) {
        int size = size(requestedSize);
        var summary = repository.summarize(sellerId, filter);
        int totalPages = pages(summary.count(), size);
        int page = page(requestedPage, totalPages);
        List<SaleRow> items = repository.list(sellerId, filter, size, (page - 1) * size);
        return new SalesPage(items, page, size, summary.count(), totalPages, summary);
    }

    @Transactional(readOnly = true)
    public SaleDetail detail(UUID sellerId, UUID chargeId) {
        SaleDetail found = repository.find(sellerId, chargeId).orElseThrow(
                () -> new NotFoundException("SALE_NOT_FOUND", "Venda não encontrada"));
        return new SaleDetail(found.id(), found.code(), found.status(), found.type(), found.productName(),
                found.productId(), found.offerName(), found.method(), found.installments(), found.createdAt(),
                found.approvedAt(), found.availableAt(), found.reference(), found.cycleNumber(),
                found.subscriptionId(), found.couponCode(), found.buyer(), found.amounts(), found.split(),
                payoutState(found, clock.instant()), found.canRefund(), found.refunds());
    }

    /** Estado do repasse: aguardando pagamento, a liberar (garantia), liberado ou devolvido. */
    static PayoutState payoutState(SaleDetail sale, Instant now) {
        return switch (sale.status()) {
            case "PENDING" -> PayoutState.WAITING_PAYMENT;
            case "REFUNDED", "CHARGEBACK" -> PayoutState.REFUNDED;
            case "PAID", "PARTIALLY_REFUNDED" -> sale.availableAt() != null && now.isBefore(sale.availableAt())
                    ? PayoutState.TO_RELEASE : PayoutState.RELEASED;
            default -> PayoutState.NOT_APPLICABLE;
        };
    }

    @Transactional(readOnly = true)
    public List<SaleRow> export(UUID sellerId, SalesFilter filter) {
        return repository.list(sellerId, filter, MAX_EXPORT_ROWS, 0);
    }

    /** Valida o que veio da URL e monta o filtro de reembolsos. */
    public RefundFilter refundFilter(String query, List<String> statuses, List<String> origins, String from, String to) {
        Set<String> parsedStatuses = parse(statuses, REFUND_STATUSES, "REFUND_STATUS_INVALID", "Status de reembolso inválido", "status");
        Set<String> parsedOrigins = parse(origins, REFUND_ORIGINS, "REFUND_ORIGIN_INVALID", "Autor do reembolso inválido", "origin");
        String text = query == null || query.isBlank() ? null : query.strip();
        if (text != null && text.length() > 120) {
            throw new ValidationException("SALES_QUERY_TOO_LONG", "A busca deve ter no máximo 120 caracteres", "q");
        }
        LocalDate start = date(from, "from");
        LocalDate end = date(to, "to");
        if (start != null && end != null && end.isBefore(start)) {
            throw new ValidationException("SALES_PERIOD_INVALID", "A data final deve ser igual ou posterior à inicial", "to");
        }
        return new RefundFilter(text, parsedStatuses, parsedOrigins, start, end);
    }

    @Transactional(readOnly = true)
    public RefundsPage refunds(UUID sellerId, RefundFilter filter, Integer requestedPage, Integer requestedSize) {
        int size = size(requestedSize);
        long total = repository.countRefunds(sellerId, filter);
        int totalPages = pages(total, size);
        int page = page(requestedPage, totalPages);
        return new RefundsPage(repository.listRefunds(sellerId, filter, size, (page - 1) * size),
                page, size, total, totalPages);
    }

    @Transactional(readOnly = true)
    public List<RefundRow> exportRefunds(UUID sellerId, RefundFilter filter) {
        return repository.listRefunds(sellerId, filter, MAX_EXPORT_ROWS, 0);
    }

    private static Set<String> parse(List<String> values, Set<String> allowed, String code, String message, String field) {
        Set<String> parsed = new LinkedHashSet<>();
        for (String value : values == null ? List.<String>of() : values) {
            String normalized = value == null ? "" : value.strip().toUpperCase();
            if (normalized.isEmpty()) continue;
            if (!allowed.contains(normalized)) throw new ValidationException(code, message, field);
            parsed.add(normalized);
        }
        return parsed;
    }

    private static LocalDate date(String value, String field) {
        if (value == null || value.isBlank()) return null;
        try {
            return LocalDate.parse(value.strip());
        } catch (DateTimeParseException error) {
            throw new ValidationException("SALES_DATE_INVALID", "Use datas no formato AAAA-MM-DD", field);
        }
    }

    private static int size(Integer requested) {
        return requested == null ? DEFAULT_PAGE_SIZE : Math.max(1, Math.min(requested, MAX_PAGE_SIZE));
    }

    private static int pages(long total, int size) {
        return (int) Math.max(1, (total + size - 1) / size);
    }

    private static int page(Integer requested, int totalPages) {
        return Math.max(1, Math.min(requested == null ? 1 : requested, totalPages));
    }
}
