package com.paysi.fiscal.app;

import com.paysi.fiscal.domain.FiscalProfile;
import com.paysi.fiscal.issuer.InvoiceCancelResult;
import com.paysi.fiscal.issuer.InvoiceIssueResult;
import com.paysi.fiscal.issuer.InvoiceIssuer;
import com.paysi.fiscal.port.FiscalProfileRepository;
import com.paysi.fiscal.port.InvoiceRepository;
import com.paysi.fiscal.port.InvoiceRepository.ClaimedInvoice;
import com.paysi.webhook.app.OutboxService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class InvoiceIssuingServiceTest {
    private static final UUID INVOICE = UUID.randomUUID();
    private static final UUID CHARGE = UUID.randomUUID();
    private static final UUID SELLER = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-09-17T00:00:00Z");

    @Test
    void issuesInvoiceAndEmitsIssuedEventOnSuccess() {
        var invoices = mock(InvoiceRepository.class);
        var profiles = mock(FiscalProfileRepository.class);
        var issuer = mock(InvoiceIssuer.class);
        var outbox = mock(OutboxService.class);
        when(invoices.claimDue(NOW)).thenReturn(Optional.of(
                new ClaimedInvoice(INVOICE, CHARGE, SELLER, "QUEUED", null, 5_000, 0)));
        when(profiles.findByAccount(SELLER)).thenReturn(Optional.of(
                new FiscalProfile(SELLER, "3550308", "12345", "1.05", 200, "SIMPLES", "vault://cred-1", NOW)));
        when(issuer.issue(any())).thenReturn(new InvoiceIssueResult(true, "prov-1", "NFSe-1", "https://x/1", null));
        var service = new InvoiceIssuingService(invoices, profiles, issuer, outbox, Clock.fixed(NOW, ZoneOffset.UTC));

        boolean processed = service.processNext();

        assertThat(processed).isTrue();
        verify(invoices).markIssued(eq(INVOICE), eq("prov-1"), eq("NFSe-1"), eq("https://x/1"), eq(NOW), eq(1));
        verify(outbox).append(eq(SELLER), eq("invoice.issued"), any());
        verify(outbox, never()).append(any(), eq("invoice.failed"), any());
    }

    @Test
    void schedulesRetryWithBackoffOnIssueFailureBeforeExhaustion() {
        var invoices = mock(InvoiceRepository.class);
        var profiles = mock(FiscalProfileRepository.class);
        var issuer = mock(InvoiceIssuer.class);
        var outbox = mock(OutboxService.class);
        when(invoices.claimDue(NOW)).thenReturn(Optional.of(
                new ClaimedInvoice(INVOICE, CHARGE, SELLER, "QUEUED", null, 5_000, 0)));
        when(profiles.findByAccount(SELLER)).thenReturn(Optional.of(
                new FiscalProfile(SELLER, "3550308", "12345", "1.05", 200, "SIMPLES", "vault://cred-1", NOW)));
        when(issuer.issue(any())).thenReturn(new InvoiceIssueResult(false, null, null, null, "ISSUER_TIMEOUT"));
        var service = new InvoiceIssuingService(invoices, profiles, issuer, outbox, Clock.fixed(NOW, ZoneOffset.UTC));

        service.processNext();

        verify(invoices).markIssueRetry(eq(INVOICE), eq("ISSUER_TIMEOUT"), eq(1), any());
        verify(invoices, never()).markIssueFailedTerminal(any(), any(), anyInt());
        verify(outbox, never()).append(any(), eq("invoice.failed"), any());
    }

    @Test
    void marksTerminalFailureAndAlertsWhenRetriesExhausted() {
        var invoices = mock(InvoiceRepository.class);
        var profiles = mock(FiscalProfileRepository.class);
        var issuer = mock(InvoiceIssuer.class);
        var outbox = mock(OutboxService.class);
        when(invoices.claimDue(NOW)).thenReturn(Optional.of(
                new ClaimedInvoice(INVOICE, CHARGE, SELLER, "QUEUED", null, 5_000, 5)));
        when(profiles.findByAccount(SELLER)).thenReturn(Optional.of(
                new FiscalProfile(SELLER, "3550308", "12345", "1.05", 200, "SIMPLES", "vault://cred-1", NOW)));
        when(issuer.issue(any())).thenReturn(new InvoiceIssueResult(false, null, null, null, "ISSUER_TIMEOUT"));
        var service = new InvoiceIssuingService(invoices, profiles, issuer, outbox, Clock.fixed(NOW, ZoneOffset.UTC));

        service.processNext();

        verify(invoices).markIssueFailedTerminal(INVOICE, "ISSUER_TIMEOUT", 6);
        verify(outbox).append(eq(SELLER), eq("invoice.failed"), any());
    }

    @Test
    void failsFastWhenFiscalProfileNoLongerValidated() {
        var invoices = mock(InvoiceRepository.class);
        var profiles = mock(FiscalProfileRepository.class);
        var issuer = mock(InvoiceIssuer.class);
        var outbox = mock(OutboxService.class);
        when(invoices.claimDue(NOW)).thenReturn(Optional.of(
                new ClaimedInvoice(INVOICE, CHARGE, SELLER, "QUEUED", null, 5_000, 0)));
        when(profiles.findByAccount(SELLER)).thenReturn(Optional.empty());
        var service = new InvoiceIssuingService(invoices, profiles, issuer, outbox, Clock.fixed(NOW, ZoneOffset.UTC));

        service.processNext();

        verifyNoInteractions(issuer);
        verify(invoices).markIssueFailedTerminal(INVOICE, "FISCAL_PROFILE_NOT_VALIDATED", 1);
        verify(outbox).append(eq(SELLER), eq("invoice.failed"), any());
    }

    @Test
    void cancelsInvoiceOnCancelRequestSuccess() {
        var invoices = mock(InvoiceRepository.class);
        var profiles = mock(FiscalProfileRepository.class);
        var issuer = mock(InvoiceIssuer.class);
        var outbox = mock(OutboxService.class);
        when(invoices.claimDue(NOW)).thenReturn(Optional.of(
                new ClaimedInvoice(INVOICE, CHARGE, SELLER, "CANCEL_REQUESTED", "prov-1", 5_000, 0)));
        when(issuer.cancel(any())).thenReturn(new InvoiceCancelResult(true, null));
        var service = new InvoiceIssuingService(invoices, profiles, issuer, outbox, Clock.fixed(NOW, ZoneOffset.UTC));

        service.processNext();

        verify(invoices).markCanceled(INVOICE, 1);
        verifyNoInteractions(profiles);
    }

    @Test
    void returnsFalseWhenNothingToClaim() {
        var invoices = mock(InvoiceRepository.class);
        var profiles = mock(FiscalProfileRepository.class);
        var issuer = mock(InvoiceIssuer.class);
        var outbox = mock(OutboxService.class);
        when(invoices.claimDue(NOW)).thenReturn(Optional.empty());
        var service = new InvoiceIssuingService(invoices, profiles, issuer, outbox, Clock.fixed(NOW, ZoneOffset.UTC));

        assertThat(service.processNext()).isFalse();
        assertThat(service.processBatch(10)).isZero();
    }
}
