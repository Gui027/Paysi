package com.paysi.observability.alert.port;

import com.paysi.observability.alert.domain.AlertEvent;

/**
 * Canal observável por humano. Uma falha aqui nunca pode propagar para quem
 * disparou o alerta (ex.: o job de integridade não pode parar de rodar porque
 * o Slack está fora do ar) — por isso {@link com.paysi.observability.alert.app.AlertService}
 * grava a evidência antes de chamar o canal e trata falha de envio como aviso,
 * não como erro fatal.
 */
public interface AlertChannel {
    void send(AlertEvent event);
}
