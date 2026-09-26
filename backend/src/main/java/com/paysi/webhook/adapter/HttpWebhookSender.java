package com.paysi.webhook.adapter;

import com.paysi.webhook.port.WebhookSender;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

@Component
public class HttpWebhookSender implements WebhookSender {
    private static final int MAX_RESPONSE_BYTES = 4096;
    private final HttpClient client;
    public HttpWebhookSender() { this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build()); }
    HttpWebhookSender(HttpClient client) { this.client = client; }

    @Override public SendResult send(String url, String body, Map<String, String> headers) {
        try {
            var builder = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10)).header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body));
            headers.forEach(builder::header);
            var response = client.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
            String responseBody;
            try (var stream = response.body()) {
                responseBody = new String(stream.readNBytes(MAX_RESPONSE_BYTES), java.nio.charset.StandardCharsets.UTF_8);
            }
            return new SendResult(response.statusCode(), response.statusCode() >= 200 && response.statusCode() < 300 ? null : "HTTP_" + response.statusCode(), responseBody);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return new SendResult(0, "INTERRUPTED");
        } catch (Exception exception) {
            return new SendResult(0, exception.getClass().getSimpleName());
        }
    }
}
