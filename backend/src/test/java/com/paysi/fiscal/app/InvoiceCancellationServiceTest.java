package com.paysi.fiscal.app;

import com.paysi.fiscal.port.InvoiceRepository;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.mockito.Mockito.*;

class InvoiceCancellationServiceTest {
    private static final UUID CHARGE = UUID.randomUUID();

    @Test
    void requestsCancellationForCharge() {
        var invoices = mock(InvoiceRepository.class);
        when(invoices.requestCancellation(CHARGE)).thenReturn(true);
        var service = new InvoiceCancellationService(invoices);

        service.requestCancellationForRefund(CHARGE);

        verify(invoices).requestCancellation(CHARGE);
    }

    @Test
    void neverThrowsWhenRepositoryFails() {
        var invoices = mock(InvoiceRepository.class);
        when(invoices.requestCancellation(CHARGE)).thenThrow(new RuntimeException("boom"));
        var service = new InvoiceCancellationService(invoices);

        service.requestCancellationForRefund(CHARGE);
    }
}
