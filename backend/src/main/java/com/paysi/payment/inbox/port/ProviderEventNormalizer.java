package com.paysi.payment.inbox.port;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.paysi.payment.inbox.domain.ProviderEventPayload;

/**
 * Traduz o corpo bruto do webhook de um provedor para o formato canônico interno
 * ({@link ProviderEventPayload}). Cada provedor tem seu próprio formato de evento —
 * a Asaas, por exemplo, aninha os dados em {@code {"event":..., "payment": {...}}},
 * bem diferente do formato já normalizado usado internamente.
 */
public interface ProviderEventNormalizer {
    /**
     * @throws JsonProcessingException payload malformado
     * @throws IllegalArgumentException campo obrigatório ausente/inválido (ex.: externalReference não é um UUID)
     */
    ProviderEventPayload normalize(String provider, String rawPayload) throws JsonProcessingException;
}
