package com.paysi.fiscal.app;

import com.paysi.fiscal.domain.FiscalProfile;
import com.paysi.fiscal.port.FiscalProfileRepository;
import com.paysi.fiscal.port.InvoiceRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class InvoiceQueueServiceTest {
    private static final UUID CHARGE = UUID.randomUUID();
    private static final UUID SELLER = UUID.randomUUID();

    @Test
    void enqueuesWhenSellerHasValidatedProfile() {
        var profiles = mock(FiscalProfileRepository.class);
        var invoices = mock(InvoiceRepository.class);
        when(profiles.findByAccount(SELLER)).thenReturn(Optional.of(
                new FiscalProfile(SELLER, "3550308", "12345", "1.05", 200, "SIMPLES", "vault://cred-1",
                        Instant.now())));
        var service = new InvoiceQueueService(profiles, invoices);

        service.enqueueAfterSale(CHARGE, SELLER);

        verify(invoices).enqueue(any(), eq(CHARGE), eq(SELLER));
    }

    @Test
    void doesNotEnqueueWhenSellerHasNoFiscalProfile() {
        var profiles = mock(FiscalProfileRepository.class);
        var invoices = mock(InvoiceRepository.class);
        when(profiles.findByAccount(SELLER)).thenReturn(Optional.empty());
        var service = new InvoiceQueueService(profiles, invoices);

        service.enqueueAfterSale(CHARGE, SELLER);

        verifyNoInteractions(invoices);
    }

    @Test
    void doesNotEnqueueWhenProfileNotYetValidated() {
        var profiles = mock(FiscalProfileRepository.class);
        var invoices = mock(InvoiceRepository.class);
        when(profiles.findByAccount(SELLER)).thenReturn(Optional.of(
                new FiscalProfile(SELLER, "3550308", "12345", "1.05", 200, "SIMPLES", "vault://cred-1", null)));
        var service = new InvoiceQueueService(profiles, invoices);

        service.enqueueAfterSale(CHARGE, SELLER);

        verifyNoInteractions(invoices);
    }

    @Test
    void neverThrowsEvenWhenRepositoryFails() {
        var profiles = mock(FiscalProfileRepository.class);
        var invoices = mock(InvoiceRepository.class);
        when(profiles.findByAccount(SELLER)).thenThrow(new RuntimeException("boom"));
        var service = new InvoiceQueueService(profiles, invoices);

        service.enqueueAfterSale(CHARGE, SELLER);
    }
}
