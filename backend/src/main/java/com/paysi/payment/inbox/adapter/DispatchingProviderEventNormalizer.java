package com.paysi.payment.inbox.adapter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paysi.payment.inbox.domain.ProviderEventPayload;
import com.paysi.payment.inbox.port.ProviderEventNormalizer;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Ponto único de tradução de webhook por provedor. Provedores "já normalizados" (hoje:
 * {@code fake}, usado em teste/local) continuam sendo lidos como {@link ProviderEventPayload}
 * direto — mesmo comportamento de antes desta classe existir. A Asaas manda um formato
 * próprio (ver {@link #normalizeAsaas}) que precisa ser traduzido antes.
 */
@Component
public class DispatchingProviderEventNormalizer implements ProviderEventNormalizer {
    private static final DateTimeFormatter ASAAS_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final ZoneId ASAAS_ZONE = ZoneId.of("America/Sao_Paulo");

    private final ObjectMapper json;

    public DispatchingProviderEventNormalizer(ObjectMapper json) {
        this.json = json;
    }

    @Override
    public ProviderEventPayload normalize(String provider, String rawPayload) throws JsonProcessingException {
        if ("asaas".equals(provider)) return normalizeAsaas(rawPayload);
        return json.readValue(rawPayload, ProviderEventPayload.class);
    }

    /**
     * Formato real do webhook de cobrança da Asaas:
     * {@code {"id":"evt_...","event":"PAYMENT_RECEIVED","dateCreated":"2024-06-12 16:45:03",
     * "payment":{"id":"pay_...","externalReference":"<orderId>","status":"RECEIVED",...}}}
     * — bem diferente do {@link ProviderEventPayload} já achatado que os outros provedores usam.
     */
    private ProviderEventPayload normalizeAsaas(String rawPayload) throws JsonProcessingException {
        var event = json.readValue(rawPayload, AsaasWebhookEvent.class);
        if (event.payment() == null) {
            throw new IllegalArgumentException("Webhook da Asaas sem objeto 'payment'");
        }
        UUID chargeId = UUID.fromString(event.payment().externalReference());
        return new ProviderEventPayload(event.id(), toCanonicalEventType(event.event()), chargeId,
                event.payment().id(), toInstant(event.dateCreated()));
    }

    // Só remapeamos para o vocabulário canônico (PAYMENT_CONFIRMED/PAYMENT_EXPIRED) os eventos
    // que o JdbcProviderEventRepository.applyEffect sabe tratar. Estorno/exclusão NÃO viram
    // PAYMENT_DECLINED aqui de propósito: isso sobrescreveria uma cobrança já PAID como FAILED,
    // e o fluxo de estorno correto é o retorno de AsaasPaymentProvider.refund(), não este webhook.
    // Ficam com prefixo ASAAS_ (guardados/auditáveis, sem efeito automático na cobrança) até
    // terem uma trilha própria de reconciliação.
    private static String toCanonicalEventType(String asaasEvent) {
        if (asaasEvent == null) return "ASAAS_UNKNOWN";
        return switch (asaasEvent) {
            case "PAYMENT_CONFIRMED", "PAYMENT_RECEIVED", "PAYMENT_RECEIVED_IN_CASH" -> "PAYMENT_CONFIRMED";
            case "PAYMENT_OVERDUE" -> "PAYMENT_EXPIRED";
            default -> "ASAAS_" + asaasEvent;
        };
    }

    private static Instant toInstant(String asaasTimestamp) {
        if (asaasTimestamp == null || asaasTimestamp.isBlank()) {
            throw new IllegalArgumentException("Webhook da Asaas sem dateCreated");
        }
        return LocalDateTime.parse(asaasTimestamp, ASAAS_TIMESTAMP).atZone(ASAAS_ZONE).toInstant();
    }

    private record AsaasWebhookEvent(String id, String event, String dateCreated, AsaasWebhookPayment payment) {
    }

    private record AsaasWebhookPayment(String id, String externalReference, String status, BigDecimal value) {
    }
}
