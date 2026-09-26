package com.paysi.webhook.port;

import java.util.Map;

public interface WebhookSender {
    SendResult send(String url, String body, Map<String, String> headers);

    /** {@code responseBody}: começo da resposta do destino (truncado), para a tela de logs. */
    record SendResult(int statusCode, String error, String responseBody) {
        public SendResult(int statusCode, String error) { this(statusCode, error, null); }

        public boolean successful() { return statusCode >= 200 && statusCode < 300 && error == null; }
    }
}
