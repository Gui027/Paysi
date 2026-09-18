package com.paysi.checkout.dispute.app;

import java.time.Instant;

/**
 * BE-12.2: abertura de contestação (RF-073/RF-074). {@code providerDisputeId} é a chave natural
 * de idempotência — a mesma notificação do adquirente entregue duas vezes não abre duas disputas.
 */
public record DisputeCommand(String providerDisputeId, String reason, Long amountCents, Long acquirerFeeCents,
                              Instant deadlineAt) {
}
