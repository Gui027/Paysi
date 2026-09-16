package com.paysi.checkout.order.port;

import java.util.Optional;
import java.util.UUID;

/**
 * Atribuição por último clique válido. Somente leitura: registrar o clique, aplicar
 * recorrência e liquidar a comissão são responsabilidade do BE-09.2. Enquanto aquele
 * cartão não existir, {@code affiliate_clicks} estará vazia e todo pedido nasce sem
 * afiliado — o que é o comportamento correto, não uma falha.
 */
public interface AffiliationClickLookup {

    Optional<Attribution> findLastClick(String visitorKey, UUID productId);

    /** @param commissionBps comissão congelada na aprovação da afiliação (RF-046) */
    record Attribution(UUID affiliationId, int commissionBps) { }
}
