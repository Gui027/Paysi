package com.paysi.observability.alert.port;

import com.paysi.observability.alert.domain.AlertEvent;

/**
 * Evidência durável de que o alerta foi gerado, independente de o canal de
 * entrega (Slack, e-mail, PagerDuty) estar disponível no momento. É o que
 * permite provar "o alerta chegou" mesmo quando o canal externo falhou.
 */
public interface AlertRepository {
    void insert(AlertEvent event);
}
