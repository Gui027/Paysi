package com.paysi.compliance.lgpd.app;

import com.paysi.checkout.order.port.BuyerRepository;
import com.paysi.compliance.lgpd.domain.LgpdRequest;
import com.paysi.compliance.lgpd.port.LgpdRequestRepository;
import com.paysi.core.error.ConflictException;
import com.paysi.core.error.NotFoundException;
import com.paysi.core.error.ValidationException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LgpdRequestServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-17T00:00:00Z");
    private static final UUID BUYER_ID = UUID.randomUUID();
    private static final UUID ASSIGNEE = UUID.randomUUID();
    private static final UUID REQUEST_ID = UUID.randomUUID();

    @Test
    void createComputesFifteenDaySlaAndReturnsOpenRequest() {
        var repository = mock(LgpdRequestRepository.class);
        var buyers = mock(BuyerRepository.class);
        when(buyers.exists(BUYER_ID)).thenReturn(true);
        var stored = new LgpdRequest(REQUEST_ID, "BUYER", BUYER_ID.toString(), "DELETION", "OPEN",
                NOW.plus(Duration.ofDays(15)), null, null, NOW);
        when(repository.insert(any(), eq("BUYER"), eq(BUYER_ID.toString()), eq("DELETION"),
                eq(NOW.plus(Duration.ofDays(15))), eq(NOW))).thenReturn(stored);
        var service = new LgpdRequestService(repository, buyers, Clock.fixed(NOW, ZoneOffset.UTC));

        var view = service.create(new CreateLgpdRequestCommand("buyer", BUYER_ID.toString(), "deletion"));

        assertThat(view.status()).isEqualTo("OPEN");
        assertThat(view.dueAt()).isEqualTo(NOW.plus(Duration.ofDays(15)));
        assertThat(view.type()).isEqualTo("DELETION");
    }

    @Test
    void createRejectsUnknownBuyer() {
        var repository = mock(LgpdRequestRepository.class);
        var buyers = mock(BuyerRepository.class);
        when(buyers.exists(BUYER_ID)).thenReturn(false);
        var service = new LgpdRequestService(repository, buyers, Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> service.create(new CreateLgpdRequestCommand("BUYER", BUYER_ID.toString(), "ACCESS")))
                .isInstanceOf(NotFoundException.class);
        verifyNoInteractions(repository);
    }

    @Test
    void createRejectsInvalidKind() {
        var repository = mock(LgpdRequestRepository.class);
        var buyers = mock(BuyerRepository.class);
        var service = new LgpdRequestService(repository, buyers, Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> service.create(new CreateLgpdRequestCommand("BUYER", BUYER_ID.toString(), "WHATEVER")))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void assignMovesOpenRequestToInProgress() {
        var repository = mock(LgpdRequestRepository.class);
        var buyers = mock(BuyerRepository.class);
        var open = new LgpdRequest(REQUEST_ID, "BUYER", BUYER_ID.toString(), "ACCESS", "OPEN", NOW, null, null, NOW);
        var assigned = new LgpdRequest(REQUEST_ID, "BUYER", BUYER_ID.toString(), "ACCESS", "IN_PROGRESS", NOW,
                ASSIGNEE, null, NOW);
        when(repository.lockForUpdate(REQUEST_ID)).thenReturn(Optional.of(open)).thenReturn(Optional.of(assigned));
        var service = new LgpdRequestService(repository, buyers, Clock.fixed(NOW, ZoneOffset.UTC));

        var view = service.assign(REQUEST_ID, ASSIGNEE);

        assertThat(view.status()).isEqualTo("IN_PROGRESS");
        assertThat(view.assignee()).isEqualTo(ASSIGNEE);
        verify(repository).assign(REQUEST_ID, ASSIGNEE, "IN_PROGRESS");
    }

    @Test
    void assignUnknownRequestFailsFast() {
        var repository = mock(LgpdRequestRepository.class);
        var buyers = mock(BuyerRepository.class);
        when(repository.lockForUpdate(REQUEST_ID)).thenReturn(Optional.empty());
        var service = new LgpdRequestService(repository, buyers, Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> service.assign(REQUEST_ID, ASSIGNEE)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void assignAlreadyClosedRequestConflicts() {
        var repository = mock(LgpdRequestRepository.class);
        var buyers = mock(BuyerRepository.class);
        var done = new LgpdRequest(REQUEST_ID, "BUYER", BUYER_ID.toString(), "ACCESS", "DONE", NOW, ASSIGNEE,
                "ok", NOW);
        when(repository.lockForUpdate(REQUEST_ID)).thenReturn(Optional.of(done));
        var service = new LgpdRequestService(repository, buyers, Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> service.assign(REQUEST_ID, ASSIGNEE)).isInstanceOf(ConflictException.class);
    }

    @Test
    void resolveWithoutAssignmentIsRejected() {
        var repository = mock(LgpdRequestRepository.class);
        var buyers = mock(BuyerRepository.class);
        var open = new LgpdRequest(REQUEST_ID, "BUYER", BUYER_ID.toString(), "ACCESS", "OPEN", NOW, null, null, NOW);
        when(repository.lockForUpdate(REQUEST_ID)).thenReturn(Optional.of(open));
        var service = new LgpdRequestService(repository, buyers, Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> service.resolve(REQUEST_ID, "DONE", "print do atendimento"))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void resolveRequiresEvidence() {
        var repository = mock(LgpdRequestRepository.class);
        var buyers = mock(BuyerRepository.class);
        var inProgress = new LgpdRequest(REQUEST_ID, "BUYER", BUYER_ID.toString(), "ACCESS", "IN_PROGRESS", NOW,
                ASSIGNEE, null, NOW);
        when(repository.lockForUpdate(REQUEST_ID)).thenReturn(Optional.of(inProgress));
        var service = new LgpdRequestService(repository, buyers, Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> service.resolve(REQUEST_ID, "DONE", " "))
                .isInstanceOf(ValidationException.class);
        verifyNoInteractions(buyers);
    }

    @Test
    void resolvingAlreadyClosedRequestConflicts() {
        var repository = mock(LgpdRequestRepository.class);
        var buyers = mock(BuyerRepository.class);
        var rejected = new LgpdRequest(REQUEST_ID, "BUYER", BUYER_ID.toString(), "ACCESS", "REJECTED", NOW,
                ASSIGNEE, "motivo", NOW);
        when(repository.lockForUpdate(REQUEST_ID)).thenReturn(Optional.of(rejected));
        var service = new LgpdRequestService(repository, buyers, Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> service.resolve(REQUEST_ID, "DONE", "evidência"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void resolvingDeletionRequestAnonymizesBuyerButDoesNotTouchOrdersOrLedger() {
        var repository = mock(LgpdRequestRepository.class);
        var buyers = mock(BuyerRepository.class);
        var inProgress = new LgpdRequest(REQUEST_ID, "BUYER", BUYER_ID.toString(), "DELETION", "IN_PROGRESS", NOW,
                ASSIGNEE, null, NOW);
        var done = new LgpdRequest(REQUEST_ID, "BUYER", BUYER_ID.toString(), "DELETION", "DONE", NOW, ASSIGNEE,
                "solicitação confirmada por e-mail, protocolo 123", NOW);
        when(repository.lockForUpdate(REQUEST_ID)).thenReturn(Optional.of(inProgress)).thenReturn(Optional.of(done));
        when(buyers.anonymize(eq(BUYER_ID), any())).thenReturn(true);
        var service = new LgpdRequestService(repository, buyers, Clock.fixed(NOW, ZoneOffset.UTC));

        var view = service.resolve(REQUEST_ID, "done", "solicitação confirmada por e-mail, protocolo 123");

        assertThat(view.status()).isEqualTo("DONE");
        verify(buyers).anonymize(BUYER_ID, NOW);
        // A anonimização só mexe no registro vivo do comprador — nada de orders/ledger é
        // referenciado por este serviço, e nenhuma outra interação acontece com o repositório
        // de pedidos ou o razão (eles nem são dependências deste serviço, por design).
        verify(repository).resolve(REQUEST_ID, "DONE", "solicitação confirmada por e-mail, protocolo 123");
    }

    @Test
    void rejectingDeletionRequestDoesNotAnonymize() {
        var repository = mock(LgpdRequestRepository.class);
        var buyers = mock(BuyerRepository.class);
        var inProgress = new LgpdRequest(REQUEST_ID, "BUYER", BUYER_ID.toString(), "DELETION", "IN_PROGRESS", NOW,
                ASSIGNEE, null, NOW);
        var rejected = new LgpdRequest(REQUEST_ID, "BUYER", BUYER_ID.toString(), "DELETION", "REJECTED", NOW,
                ASSIGNEE, "sem base para exclusão", NOW);
        when(repository.lockForUpdate(REQUEST_ID)).thenReturn(Optional.of(inProgress))
                .thenReturn(Optional.of(rejected));
        var service = new LgpdRequestService(repository, buyers, Clock.fixed(NOW, ZoneOffset.UTC));

        var view = service.resolve(REQUEST_ID, "REJECTED", "sem base para exclusão");

        assertThat(view.status()).isEqualTo("REJECTED");
        verify(buyers, never()).anonymize(any(), any());
    }

    @Test
    void createRejectsBlankSubjectRef() {
        var repository = mock(LgpdRequestRepository.class);
        var buyers = mock(BuyerRepository.class);
        var service = new LgpdRequestService(repository, buyers, Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> service.create(new CreateLgpdRequestCommand("BUYER", " ", "ACCESS")))
                .isInstanceOf(ValidationException.class);
    }
}
