package com.paysi.checkout.dispute.app;

import java.time.Instant;
import java.util.UUID;

/**
 * RF-075: pacote de defesa montado a partir da evidência congelada em {@code sale_evidence} e dos
 * dados da própria disputa. É uma projeção determinística e somente-leitura — mesma disputa, mesma
 * evidência, mesmo pacote — sem geração de artefato binário (PDF etc.), fora do escopo do cartão.
 */
public record EvidencePackage(UUID disputeId, UUID chargeId, String disputeStatus, Instant deadlineAt,
                               long amountCents, long acquirerFeeCents, String reason, String ip, String userAgent,
                               String deviceFingerprint, String termsHash, Instant termsAcceptedAt,
                               String threeDsResult, Instant emailDeliveredAt, Instant emailOpenedAt,
                               String accessLog) {
}
