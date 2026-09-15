package com.paysi.webhook.app;

import com.paysi.core.error.ValidationException;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebhookUrlPolicyTest {
    @Test void acceptsPublicHttpsUrl() throws Exception {
        var policy = new WebhookUrlPolicy(host -> new InetAddress[]{InetAddress.getByName("8.8.8.8")});
        assertThat(policy.validate(" https://hooks.example.com/paysi ")).isEqualTo("https://hooks.example.com/paysi");
    }

    @Test void blocksHttpCredentialsCustomPortsAndPrivateAddresses() throws Exception {
        var publicDns = new WebhookUrlPolicy(host -> new InetAddress[]{InetAddress.getByName("8.8.8.8")});
        var privateDns = new WebhookUrlPolicy(host -> new InetAddress[]{InetAddress.getByName("127.0.0.1")});
        assertThatThrownBy(() -> publicDns.validate("http://hooks.example.com")).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> publicDns.validate("https://user:pass@hooks.example.com")).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> publicDns.validate("https://hooks.example.com:8443")).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> privateDns.validate("https://internal.example.com")).isInstanceOf(ValidationException.class);
    }
}
