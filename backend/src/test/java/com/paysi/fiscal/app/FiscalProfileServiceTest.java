package com.paysi.fiscal.app;

import com.paysi.core.error.NotFoundException;
import com.paysi.core.error.ValidationException;
import com.paysi.fiscal.domain.FiscalProfile;
import com.paysi.fiscal.issuer.InvoiceIssuer;
import com.paysi.fiscal.issuer.IssuerValidationResult;
import com.paysi.fiscal.port.FiscalProfileRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FiscalProfileServiceTest {
    private static final UUID SELLER = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-09-17T00:00:00Z");

    private static FiscalProfileCommand validCommand() {
        return new FiscalProfileCommand("3550308", "12345", "1.05", 200, "SIMPLES", "vault://cred-1");
    }

    @Test
    void savesAndValidatesProfileWhenIssuerApprovesCredentials() {
        var repository = mock(FiscalProfileRepository.class);
        var issuer = mock(InvoiceIssuer.class);
        when(issuer.validate(any())).thenReturn(new IssuerValidationResult(true, null));
        when(repository.findByAccount(SELLER)).thenReturn(Optional.of(
                new FiscalProfile(SELLER, "3550308", "12345", "1.05", 200, "SIMPLES", "vault://cred-1", NOW)));
        var service = new FiscalProfileService(repository, issuer, Clock.fixed(NOW, ZoneOffset.UTC));

        var result = service.saveAndValidate(SELLER, validCommand());

        assertThat(result.validated()).isTrue();
        verify(repository).upsert(SELLER, "3550308", "12345", "1.05", 200, "SIMPLES", "vault://cred-1");
        verify(repository).markValidated(SELLER, NOW);
    }

    @Test
    void rejectsWhenIssuerRefusesCredentials() {
        var repository = mock(FiscalProfileRepository.class);
        var issuer = mock(InvoiceIssuer.class);
        when(issuer.validate(any())).thenReturn(new IssuerValidationResult(false, "ISSUER_CREDENTIALS_REJECTED"));
        var service = new FiscalProfileService(repository, issuer, Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> service.saveAndValidate(SELLER, validCommand()))
                .isInstanceOf(ValidationException.class);

        verify(repository).upsert(SELLER, "3550308", "12345", "1.05", 200, "SIMPLES", "vault://cred-1");
        verify(repository, never()).markValidated(eq(SELLER), any());
    }

    @Test
    void rejectsMissingRequiredFieldsBeforeCallingIssuer() {
        var repository = mock(FiscalProfileRepository.class);
        var issuer = mock(InvoiceIssuer.class);
        var service = new FiscalProfileService(repository, issuer, Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> service.saveAndValidate(SELLER,
                new FiscalProfileCommand("", "12345", "1.05", 200, "SIMPLES", "vault://cred-1")))
                .isInstanceOf(ValidationException.class);
        verifyNoInteractions(issuer);
        verify(repository, never()).upsert(any(), any(), any(), any(), anyInt(), any(), any());
    }

    @Test
    void rejectsInvalidTaxRegime() {
        var repository = mock(FiscalProfileRepository.class);
        var issuer = mock(InvoiceIssuer.class);
        var service = new FiscalProfileService(repository, issuer, Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> service.saveAndValidate(SELLER,
                new FiscalProfileCommand("3550308", "12345", "1.05", 200, "LUCRO_REAL", "vault://cred-1")))
                .isInstanceOf(ValidationException.class);
        verifyNoInteractions(issuer);
    }

    @Test
    void findThrowsNotFoundWhenNoProfile() {
        var repository = mock(FiscalProfileRepository.class);
        var issuer = mock(InvoiceIssuer.class);
        when(repository.findByAccount(SELLER)).thenReturn(Optional.empty());
        var service = new FiscalProfileService(repository, issuer, Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> service.find(SELLER)).isInstanceOf(NotFoundException.class);
    }
}
