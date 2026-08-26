package com.paysi.identity.port;

import java.util.UUID;

/**
 * Leitura mínima do plano comercial vigente, cuja fonte única é
 * {@code platform_subscriptions} (V022). A gestão completa do plano
 * (troca, cobrança, rebaixamento) pertence ao futuro módulo billing —
 * fora do escopo deste cadastro, que só precisa confirmar e devolver o plano.
 */
public interface PlatformPlanReader {

    /** @throws IllegalStateException se a conta não tiver linha em platform_subscriptions (RF-125 quebrada) */
    String currentPlan(UUID accountId);
}
