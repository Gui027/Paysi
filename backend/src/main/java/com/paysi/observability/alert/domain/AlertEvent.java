package com.paysi.observability.alert.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Alerta operacional (documento 3, §5.1 e checklist #12): condição que precisa
 * chegar a um canal observável por humano, não só a uma linha de log. {@code
 * severity} segue a classificação de incidente do documento 3 — {@code CRITICAL}
 * é a linha das oito verificações de integridade e de dado incorreto/vazado.
 */
public record AlertEvent(UUID id, String type, String severity, String payload, Instant createdAt) {
}
