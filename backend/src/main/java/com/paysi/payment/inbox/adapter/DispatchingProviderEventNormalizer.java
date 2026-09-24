package com.paysi.payment.inbox.adapter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paysi.payment.inbox.domain.ProviderEventPayload;
import com.paysi.payment.inbox.port.ChargeLookup;
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

    // Placeholder para eventos sem efeito (ver normalizeAsaas): o payload canônico exige um chargeId.
    private static final UUID NO_CHARGE = new UUID(0L, 0L);

    private final ObjectMapper json;
    private final ChargeLookup charges;

    public DispatchingProviderEventNormalizer(ObjectMapper json, ChargeLookup charges) {
        this.json = json;
        this.charges = charges;
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
     *
     * <p>A cobrança interna é achada pelo id da Asaas ({@code payment.id}, gravado em
     * {@code charges.provider_charge_id}); o {@code externalReference} é o id do pedido, não da cobrança.
     * Eventos sem efeito (prefixo ASAAS_) usam um chargeId nulo: o {@code PAYMENT_CREATED}, por exemplo,
     * chega enquanto a transação que grava o {@code provider_charge_id} ainda está aberta, e rejeitá-lo
     * faria a Asaas penalizar (pausar) a fila de webhooks.
     */
    private ProviderEventPayload normalizeAsaas(String rawPayload) throws JsonProcessingException {
        var event = json.readValue(rawPayload, AsaasWebhookEvent.class);
        if (event.payment() == null) {
            throw new IllegalArgumentException("Webhook da Asaas sem objeto 'payment'");
        }
        String eventType = toCanonicalEventType(event.event());
        UUID chargeId = eventType.startsWith("ASAAS_")
                ? charges.findByProviderChargeId(event.payment().id()).orElse(NO_CHARGE)
                : charges.findByProviderChargeId(event.payment().id()).orElseThrow(() ->
                        new IllegalArgumentException("Cobrança da Asaas desconhecida: " + event.payment().id()));
        return new ProviderEventPayload(event.id(), eventType, chargeId,
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
