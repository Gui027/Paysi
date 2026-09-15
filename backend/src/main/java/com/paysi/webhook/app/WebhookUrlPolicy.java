package com.paysi.webhook.app;

import com.paysi.core.error.ValidationException;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Arrays;

@Component
public class WebhookUrlPolicy {
    private final AddressResolver resolver;

    public WebhookUrlPolicy() { this(InetAddress::getAllByName); }
    WebhookUrlPolicy(AddressResolver resolver) { this.resolver = resolver; }

    public String validate(String rawUrl) {
        try {
            URI uri = URI.create(rawUrl == null ? "" : rawUrl.strip());
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
                    || uri.getFragment() != null || (uri.getPort() != -1 && uri.getPort() != 443)) throw invalid();
            InetAddress[] addresses = resolver.resolve(uri.getHost());
            if (addresses.length == 0 || Arrays.stream(addresses).anyMatch(WebhookUrlPolicy::unsafe)) throw invalid();
            return uri.toASCIIString();
        } catch (IllegalArgumentException | UnknownHostException exception) {
            throw invalid();
        }
    }

    private static boolean unsafe(InetAddress address) {
        return address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress();
    }

    private static ValidationException invalid() {
        return new ValidationException("WEBHOOK_URL_UNSAFE", "Use uma URL HTTPS pública na porta padrão", "url");
    }

    interface AddressResolver { InetAddress[] resolve(String host) throws UnknownHostException; }
}
