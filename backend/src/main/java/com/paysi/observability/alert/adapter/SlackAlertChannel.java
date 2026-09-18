package com.paysi.observability.alert.adapter;

import com.paysi.observability.alert.domain.AlertEvent;
import com.paysi.observability.alert.port.AlertChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Entrega por webhook de entrada do Slack (formato {@code {"text": "..."}}).
 * Sem {@code paysi.alerts.slack-webhook-url} configurada — caso do ambiente
 * local e de CI — o canal fica em modo somente-log: a evidência em {@code
 * ops_alerts} continua sendo gravada por {@link com.paysi.observability.alert.app.AlertService}
 * de qualquer forma, então nenhum alerta some, só a entrega externa é que não
 * é tentada.
 */
@Component
public class SlackAlertChannel implements AlertChannel {
    private static final Logger log = LoggerFactory.getLogger(SlackAlertChannel.class);

    private final RestTemplate http;
    private final String webhookUrl;

    public SlackAlertChannel(RestTemplate http,
            @Value("${paysi.alerts.slack-webhook-url:}") String webhookUrl) {
        this.http = http;
        this.webhookUrl = webhookUrl;
    }

    @Override
    public void send(AlertEvent event) {
        if (webhookUrl == null || webhookUrl.isBlank()) {
            log.info("ALERT type={} severity={} (canal Slack não configurado, evidência em ops_alerts)",
                    event.type(), event.severity());
            return;
        }
        try {
            String text = "[" + event.severity() + "] " + event.type() + " — " + event.payload();
            http.postForEntity(webhookUrl, Map.of("text", text), Void.class);
        } catch (RestClientException error) {
            log.warn("Falha ao entregar alerta {} no canal Slack; evidência preservada em ops_alerts",
                    event.type(), error);
        }
    }
}
