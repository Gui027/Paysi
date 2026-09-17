package com.paysi.ledger.app;

import com.paysi.fiscal.app.InvoiceQueueService;
import com.paysi.ledger.domain.*;
import com.paysi.ledger.port.ChargeSaleRepository;
import com.paysi.ledger.port.ChargeSaleRepository.ChargeSale;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SaleLedgerServiceTest {
    private static final UUID CHARGE = UUID.randomUUID();
    private static final UUID SELLER = UUID.randomUUID();
    private static final UUID AFFILIATE = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-09-01T00:00:00Z");

    @Test
    void creditsSellerAndPlatformWithoutAffiliate() {
        var repository = mock(ChargeSaleRepository.class);
        var ledger = mock(LedgerService.class);
        when(repository.findChargeSale(CHARGE)).thenReturn(Optional.of(
                new ChargeSale(SELLER, 9_000, 1_000, null, 0, 7)));
        var invoiceQueue = mock(InvoiceQueueService.class);
        var service = new SaleLedgerService(repository, ledger, invoiceQueue);

        service.creditForCharge(CHARGE, NOW);

        var captor = org.mockito.ArgumentCaptor.forClass(LedgerCommand.class);
        verify(ledger, times(1)).write(captor.capture());
        LedgerCommand command = captor.getValue();
        assertThat(command.type()).isEqualTo(TransactionType.SALE);
        assertThat(command.reference()).isEqualTo(new LedgerReference(ReferenceType.CHARGE, CHARGE + ":sale"));
        assertThat(command.entries()).hasSize(3);
        assertThat(command.entries()).anySatisfy(entry -> {
            assertThat(entry.accountId()).isEqualTo(SELLER);
            assertThat(entry.bucket()).isEqualTo(Bucket.GUARANTEE);
            assertThat(entry.direction()).isEqualTo(Direction.CREDIT);
            assertThat(entry.amountCents()).isEqualTo(9_000);
            assertThat(entry.releaseAt()).isEqualTo(NOW.plus(Duration.ofDays(7)));
        });
        verify(invoiceQueue).enqueueAfterSale(CHARGE, SELLER);
    }

    @Test
    void alsoCreditsAffiliateCommissionWhenAttributed() {
        var repository = mock(ChargeSaleRepository.class);
        var ledger = mock(LedgerService.class);
        when(repository.findChargeSale(CHARGE)).thenReturn(Optional.of(
                new ChargeSale(SELLER, 8_500, 1_000, AFFILIATE, 500, 7)));
        var service = new SaleLedgerService(repository, ledger, mock(InvoiceQueueService.class));

        service.creditForCharge(CHARGE, NOW);

        var captor = org.mockito.ArgumentCaptor.forClass(LedgerCommand.class);
        verify(ledger, times(2)).write(captor.capture());
        var commands = captor.getAllValues();
        assertThat(commands.get(0).type()).isEqualTo(TransactionType.SALE);
        assertThat(commands.get(1).type()).isEqualTo(TransactionType.COMMISSION);
        assertThat(commands.get(1).reference()).isEqualTo(new LedgerReference(ReferenceType.CHARGE, CHARGE + ":commission"));
        assertThat(commands.get(1).entries()).anySatisfy(entry -> {
            assertThat(entry.accountId()).isEqualTo(AFFILIATE);
            assertThat(entry.bucket()).isEqualTo(Bucket.GUARANTEE);
            assertThat(entry.amountCents()).isEqualTo(500);
        });
    }

    @Test
    void skipsAffiliateTransactionWhenNoAffiliateAttributed() {
        var repository = mock(ChargeSaleRepository.class);
        var ledger = mock(LedgerService.class);
        when(repository.findChargeSale(CHARGE)).thenReturn(Optional.of(
                new ChargeSale(SELLER, 9_000, 1_000, null, 0, 7)));
        var service = new SaleLedgerService(repository, ledger, mock(InvoiceQueueService.class));

        service.creditForCharge(CHARGE, NOW);

        verify(ledger, times(1)).write(any());
    }

    @Test
    void failsFastWhenChargeHasNoSaleContext() {
        var repository = mock(ChargeSaleRepository.class);
        var ledger = mock(LedgerService.class);
        when(repository.findChargeSale(CHARGE)).thenReturn(Optional.empty());
        var service = new SaleLedgerService(repository, ledger, mock(InvoiceQueueService.class));

        assertThatThrownBy(() -> service.creditForCharge(CHARGE, NOW))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(ledger);
    }
}
