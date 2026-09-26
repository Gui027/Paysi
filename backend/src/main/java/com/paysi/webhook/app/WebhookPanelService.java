package com.paysi.webhook.app;

import com.paysi.core.error.NotFoundException;
import com.paysi.core.error.ValidationException;
import com.paysi.webhook.app.WebhookPanelModels.EndpointItem;
import com.paysi.webhook.app.WebhookPanelModels.LogDetail;
import com.paysi.webhook.app.WebhookPanelModels.LogFilter;
import com.paysi.webhook.app.WebhookPanelModels.LogsPage;
import com.paysi.webhook.port.WebhookQueryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/** Consultas das telas de Webhooks: lista de endpoints e logs de envio paginados. */
@Service
public class WebhookPanelService {
    public static final int PAGE_SIZE = 10;
    private static final Pattern EVENT = Pattern.compile("[A-Z][A-Z0-9_.-]{2,63}");

    private final WebhookQueryRepository repository;

    public WebhookPanelService(WebhookQueryRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<EndpointItem> endpoints(UUID accountId, String query, UUID productId) {
        String text = query == null || query.isBlank() ? null : query.strip();
        if (text != null && text.length() > 120) throw new ValidationException("WEBHOOK_QUERY_TOO_LONG", "A busca deve ter no máximo 120 caracteres", "q");
        return repository.endpoints(accountId, text, productId);
    }

    public LogFilter filter(List<String> events, String query, String from, String to) {
        Set<String> types = new LinkedHashSet<>();
        for (String event : events == null ? List.<String>of() : events) {
            String type = event == null ? "" : event.strip().toUpperCase();
            if (type.isEmpty()) continue;
            if (!EVENT.matcher(type).matches()) throw new ValidationException("WEBHOOK_EVENT_INVALID", "Tipo de evento inválido", "event");
            types.add(type);
        }
        LocalDate start = date(from, "from");
        LocalDate end = date(to, "to");
        if (start != null && end != null && end.isBefore(start)) {
            throw new ValidationException("WEBHOOK_PERIOD_INVALID", "A data final deve ser igual ou posterior à inicial", "to");
        }
        String text = query == null || query.isBlank() ? null : query.strip();
        if (text != null && text.length() > 120) throw new ValidationException("WEBHOOK_QUERY_TOO_LONG", "A busca deve ter no máximo 120 caracteres", "q");
        return new LogFilter(types, text, start, end);
    }

    @Transactional(readOnly = true)
    public LogsPage logs(UUID accountId, UUID endpointId, LogFilter filter, Integer requestedPage) {
        long total = repository.countLogs(accountId, endpointId, filter);
        int totalPages = (int) Math.max(1, (total + PAGE_SIZE - 1) / PAGE_SIZE);
        int page = Math.max(1, Math.min(requestedPage == null ? 1 : requestedPage, totalPages));
        return new LogsPage(repository.logs(accountId, endpointId, filter, PAGE_SIZE, (page - 1) * PAGE_SIZE), page, PAGE_SIZE, total, totalPages);
    }

    @Transactional(readOnly = true)
    public LogDetail detail(UUID accountId, UUID endpointId, UUID eventId) {
        return repository.logDetail(accountId, endpointId, eventId)
                .orElseThrow(() -> new NotFoundException("WEBHOOK_LOG_NOT_FOUND", "Registro de envio não encontrado"));
    }

    private static LocalDate date(String value, String field) {
        if (value == null || value.isBlank()) return null;
        try {
            return LocalDate.parse(value.strip());
        } catch (DateTimeParseException error) {
            throw new ValidationException("WEBHOOK_DATE_INVALID", "Data inválida. Use o formato AAAA-MM-DD", field);
        }
    }
}
