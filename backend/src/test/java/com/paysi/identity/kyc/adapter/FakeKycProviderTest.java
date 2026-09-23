package com.paysi.identity.kyc.adapter;

import com.paysi.identity.domain.KycStatus;
import com.paysi.identity.kyc.webhook.port.KycWebhookStore;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class FakeKycProviderTest {
    @Test
    void createProcessApprovesAccountImmediately() {
        var store = mock(KycWebhookStore.class);
        var provider = new FakeKycProvider(store, Clock.fixed(Instant.parse("2026-09-23T12:00:00Z"), ZoneOffset.UTC));
        var accountId = UUID.randomUUID();

        var process = provider.createProcess(accountId);

        verify(store).apply(eq(accountId), eq(KycStatus.APPROVED), eq("fake_sub_" + accountId), any(List.class));
        assertThat(process.requirements()).allMatch(requirement -> "APPROVED".equals(requirement.status()));
        assertThat(process.activeAt(Instant.parse("2026-09-23T13:00:00Z"))).isTrue();
    }
}
