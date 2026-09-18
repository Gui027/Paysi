package com.paysi.risk.app;

import com.paysi.risk.port.RiskRepository;
import com.paysi.risk.port.RiskRepository.PlatformMetrics;
import com.paysi.risk.port.RiskRepository.SellerMetrics;
import com.paysi.webhook.app.OutboxService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * RF-076 (contestação por vendedor: 0,5% / 1,0% / 1,5%), RF-077 (reembolso por vendedor:
 * 8% / 12% / 20%) e RF-106 (agregado da plataforma: 0,4% / 0,7%) — os limiares são testados
 * exatamente na borda, porque "acima ou igual" e "acima" mudam o resultado no bps exato.
 */
class RiskServiceTest {
    private static final UUID SELLER = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-09-17T00:00:00Z");

    @Test
    void belowAllSellerThresholdsTakesNoAction() {
        var fixture = fixture(metrics(1_000_000, 4_999, 79_999)); // 49bps, 799bps: um bps abaixo de cada alerta

        var snapshot = fixture.service.recalculateSeller(SELLER);

        assertThat(snapshot.disputeAction()).isNull();
        assertThat(snapshot.refundAction()).isNull();
        verify(fixture.repository, never()).updateAccountStatus(any(), any());
        verifyNoInteractions(fixture.outbox);
    }

    @Test
    void disputeRateExactlyAtAlertThresholdFiresAlertWithoutBlocking() {
        var fixture = fixture(metrics(1_000_000, 5_000, 0)); // exatamente 50 bps = 0,5%

        var snapshot = fixture.service.recalculateSeller(SELLER);

        assertThat(snapshot.chargebackBps()).isEqualTo(50);
        assertThat(snapshot.disputeAction()).isEqualTo("ALERT");
        verify(fixture.repository, never()).updateAccountStatus(any(), any());
        verify(fixture.outbox).append(eq(SELLER), eq("risk.alert"), any());
        verify(fixture.repository).insertRiskEvent(any(), eq(SELLER), eq("ALERT"), eq("dispute_rate"), eq(50),
                eq(50), any(), eq(NOW), eq(NOW));
    }

    @Test
    void disputeRateExactlyAtSuspendThresholdSuspendsSalesAndNotifiesFirst() {
        var fixture = fixture(metrics(1_000_000, 10_000, 0)); // exatamente 100 bps = 1,0%

        var snapshot = fixture.service.recalculateSeller(SELLER);

        assertThat(snapshot.disputeAction()).isEqualTo("SUSPEND");
        var inOrder = inOrder(fixture.outbox, fixture.repository);
        inOrder.verify(fixture.outbox).append(eq(SELLER), eq("risk.account_restricted"), any());
        inOrder.verify(fixture.repository).updateAccountStatus(SELLER, "SUSPENDED");
    }

    @Test
    void disputeRateExactlyAtLimitThresholdBlocksBalance() {
        var fixture = fixture(metrics(1_000_000, 15_000, 0)); // exatamente 150 bps = 1,5%

        var snapshot = fixture.service.recalculateSeller(SELLER);

        assertThat(snapshot.disputeAction()).isEqualTo("LIMIT");
        verify(fixture.repository).updateAccountStatus(SELLER, "LIMITED");
    }

    @Test
    void disputeRateOneBpsBelowAlertThresholdTakesNoAction() {
        var fixture = fixture(metrics(1_000_000, 4_999, 0)); // 49 bps

        var snapshot = fixture.service.recalculateSeller(SELLER);

        assertThat(snapshot.disputeAction()).isNull();
    }

    @Test
    void refundRateExactlyAtAlertThresholdFiresAlert() {
        var fixture = fixture(metrics(1_000_000, 0, 80_000)); // exatamente 800 bps = 8%

        var snapshot = fixture.service.recalculateSeller(SELLER);

        assertThat(snapshot.refundAction()).isEqualTo("ALERT");
    }

    @Test
    void refundRateExactlyAtReviewThresholdLimitsAccount() {
        var fixture = fixture(metrics(1_000_000, 0, 120_000)); // exatamente 1200 bps = 12%

        var snapshot = fixture.service.recalculateSeller(SELLER);

        assertThat(snapshot.refundAction()).isEqualTo("LIMIT");
        verify(fixture.repository).updateAccountStatus(SELLER, "LIMITED");
    }

    @Test
    void refundRateExactlyAtSuspendThresholdSuspendsAccount() {
        var fixture = fixture(metrics(1_000_000, 0, 200_000)); // exatamente 2000 bps = 20%

        var snapshot = fixture.service.recalculateSeller(SELLER);

        assertThat(snapshot.refundAction()).isEqualTo("SUSPEND");
        verify(fixture.repository).updateAccountStatus(SELLER, "SUSPENDED");
    }

    @Test
    void zeroVolumeNeverDividesByZero() {
        var fixture = fixture(metrics(0, 0, 0));

        var snapshot = fixture.service.recalculateSeller(SELLER);

        assertThat(snapshot.chargebackBps()).isZero();
        assertThat(snapshot.refundAction()).isNull();
    }

    @Test
    void platformIndexBelowAlertThresholdDoesNotNotify() {
        var repository = mock(RiskRepository.class);
        var outbox = mock(OutboxService.class);
        when(repository.platformMetrics()).thenReturn(new PlatformMetrics(1_000_000, 3_999, 0)); // 39 bps
        var service = new RiskService(repository, outbox, Clock.fixed(NOW, ZoneOffset.UTC));

        var snapshot = service.recalculatePlatform();

        assertThat(snapshot.alerted()).isFalse();
        assertThat(snapshot.frozen()).isFalse();
        verifyNoInteractions(outbox);
    }

    @Test
    void platformIndexExactlyAtAlertThresholdNotifiesWithoutFreezing() {
        var repository = mock(RiskRepository.class);
        var outbox = mock(OutboxService.class);
        when(repository.platformMetrics()).thenReturn(new PlatformMetrics(1_000_000, 4_000, 0)); // exatamente 40 bps
        var service = new RiskService(repository, outbox, Clock.fixed(NOW, ZoneOffset.UTC));

        var snapshot = service.recalculatePlatform();

        assertThat(snapshot.alerted()).isTrue();
        assertThat(snapshot.frozen()).isFalse();
        verify(outbox).append(any(), eq("risk.platform_alert"), any());
    }

    @Test
    void platformIndexExactlyAtFreezeThresholdFreezesNewAccountApproval() {
        var repository = mock(RiskRepository.class);
        var outbox = mock(OutboxService.class);
        when(repository.platformMetrics()).thenReturn(new PlatformMetrics(1_000_000, 7_000, 0)); // exatamente 70 bps
        var service = new RiskService(repository, outbox, Clock.fixed(NOW, ZoneOffset.UTC));

        var snapshot = service.recalculatePlatform();

        assertThat(snapshot.frozen()).isTrue();
        verify(outbox).append(any(), eq("risk.platform_freeze_new_accounts"), any());
        verify(outbox, never()).append(any(), eq("risk.platform_alert"), any());
    }

    private static SellerMetrics metrics(long volume, long disputed, long refunded) {
        return new SellerMetrics(volume, disputed, refunded);
    }

    private static Fixture fixture(SellerMetrics metrics) {
        var repository = mock(RiskRepository.class);
        var outbox = mock(OutboxService.class);
        when(repository.sellerMetrics(SELLER)).thenReturn(metrics);
        var service = new RiskService(repository, outbox, Clock.fixed(NOW, ZoneOffset.UTC));
        return new Fixture(service, repository, outbox);
    }

    private record Fixture(RiskService service, RiskRepository repository, OutboxService outbox) {
    }
}
