package com.paysi.webhook.port;

import java.util.Map;

public interface WebhookSender {
    SendResult send(String url, String body, Map<String, String> headers);

    record SendResult(int statusCode, String error) {
        public boolean successful() { return statusCode >= 200 && statusCode < 300 && error == null; }
    }
}
