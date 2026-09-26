package com.paysi.webhook.app;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Modelos de leitura das telas de Webhooks (lista de endpoints e logs de envio). */
public final class WebhookPanelModels {
    private WebhookPanelModels() { }

    /** Estado do último envio de um evento: sucesso, falhou sem novas tentativas ou falhou e ainda vai tentar de novo. */
    public static final String SUCCESS = "SUCCESS";
    public static final String FAILED = "FAILED";
    public static final String RETRYING = "RETRYING";

    public record EndpointItem(UUID id, String name, String url, UUID productId, String productName, Set<String> events,
                               boolean enabled, Instant createdAt) { }

    public record LogFilter(Set<String> eventTypes, String query, LocalDate from, LocalDate to) { }

    public record LogRow(UUID eventId, String eventType, String saleCode, Instant sentAt, String status, Integer statusCode, int attempts) { }

    public record LogsPage(List<LogRow> items, int page, int size, long total, int totalPages) { }

    public record LogDetail(UUID eventId, String eventType, String url, Instant sentAt, String status, Integer statusCode,
                            String error, String requestBody, String responseBody, int attempts, boolean canResend) { }

    public record BulkResendResult(int sent) { }

    public static String statusOf(Integer statusCode, String error, Instant nextRetryAt) {
        if (statusCode != null && statusCode >= 200 && statusCode < 300 && error == null) return SUCCESS;
        return nextRetryAt != null ? RETRYING : FAILED;
    }
}
