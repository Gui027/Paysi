package com.paysi.sales.app;

import com.paysi.core.error.NotFoundException;
import com.paysi.core.error.ValidationException;
import com.paysi.sales.app.SubscriptionsModels.SubscriptionDetail;
import com.paysi.sales.app.SubscriptionsModels.SubscriptionRow;
import com.paysi.sales.app.SubscriptionsModels.SubscriptionsFilter;
import com.paysi.sales.app.SubscriptionsModels.SubscriptionsPage;
import com.paysi.sales.port.SubscriptionsQueryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class SubscriptionsService {
    public static final int DEFAULT_PAGE_SIZE = 10;
    public static final int MAX_PAGE_SIZE = 50;
    public static final int MAX_EXPORT_ROWS = 10_000;

    private final SubscriptionsQueryRepository repository;

    public SubscriptionsService(SubscriptionsQueryRepository repository) {
        this.repository = repository;
    }

    /** Valida cada texto vindo da URL antes de virar consulta. */
    public SubscriptionsFilter filter(String tab, String query, List<String> statuses, String cycle, String method,
                                      UUID productId, String from, String to) {
        String normalizedTab = tab == null ? "active" : tab.strip().toLowerCase();
        if (!Set.of("active", "canceled", "all").contains(normalizedTab)) {
            throw new ValidationException("SUBSCRIPTIONS_TAB_INVALID", "Aba de assinaturas inválida", "tab");
        }
        Set<String> parsedStatuses = new LinkedHashSet<>();
        for (String status : statuses == null ? List.<String>of() : statuses) {
            String normalized = status == null ? "" : status.strip().toUpperCase();
            if (normalized.isEmpty()) continue;
            if (!SubscriptionsModels.STATUSES.contains(normalized)) {
                throw new ValidationException("SUBSCRIPTIONS_STATUS_INVALID", "Status de assinatura inválido", "status");
            }
            parsedStatuses.add(normalized);
        }
        String normalizedCycle = blankToNull(cycle) == null ? null : cycle.strip().toUpperCase();
        if (normalizedCycle != null && !SubscriptionsModels.CYCLES.contains(normalizedCycle)) {
            throw new ValidationException("SUBSCRIPTIONS_CYCLE_INVALID", "Frequência inválida", "cycle");
        }
        String normalizedMethod = blankToNull(method) == null ? null : method.strip().toUpperCase();
        if (normalizedMethod != null && !SalesModels.METHODS.contains(normalizedMethod)) {
            throw new ValidationException("SALES_METHOD_INVALID", "Meio de pagamento inválido", "method");
        }
        LocalDate start = date(from, "from");
        LocalDate end = date(to, "to");
        if (start != null && end != null && end.isBefore(start)) {
            throw new ValidationException("SALES_PERIOD_INVALID", "A data final deve ser igual ou posterior à inicial", "to");
        }
        String text = blankToNull(query);
        if (text != null && text.length() > 120) {
            throw new ValidationException("SALES_QUERY_TOO_LONG", "A busca deve ter no máximo 120 caracteres", "q");
        }
        return new SubscriptionsFilter(normalizedTab, text, parsedStatuses, normalizedCycle, normalizedMethod,
                productId, start, end);
    }

    @Transactional(readOnly = true)
    public SubscriptionsPage list(UUID sellerId, SubscriptionsFilter filter, Integer requestedPage, Integer requestedSize) {
        int size = requestedSize == null ? DEFAULT_PAGE_SIZE : Math.max(1, Math.min(requestedSize, MAX_PAGE_SIZE));
        long total = repository.count(sellerId, filter);
        int totalPages = (int) Math.max(1, (total + size - 1) / size);
        int page = Math.max(1, Math.min(requestedPage == null ? 1 : requestedPage, totalPages));
        var summary = repository.summarize(sellerId, filter);
        return new SubscriptionsPage(repository.list(sellerId, filter, size, (page - 1) * size), page, size, total,
                totalPages, summary);
    }

    @Transactional(readOnly = true)
    public List<SubscriptionRow> export(UUID sellerId, SubscriptionsFilter filter) {
        return repository.list(sellerId, filter, MAX_EXPORT_ROWS, 0);
    }

    @Transactional(readOnly = true)
    public SubscriptionDetail detail(UUID sellerId, UUID subscriptionId) {
        return repository.find(sellerId, subscriptionId).orElseThrow(
                () -> new NotFoundException("SUBSCRIPTION_NOT_FOUND", "Assinatura não encontrada"));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static LocalDate date(String value, String field) {
        if (value == null || value.isBlank()) return null;
        try {
            return LocalDate.parse(value.strip());
        } catch (DateTimeParseException error) {
            throw new ValidationException("SALES_DATE_INVALID", "Use datas no formato AAAA-MM-DD", field);
        }
    }
}
