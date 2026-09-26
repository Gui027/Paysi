package com.paysi.collaborator.app;

import com.paysi.collaborator.app.CollaboratorModels.Collaborator;
import com.paysi.collaborator.port.CollaboratorMailSender;
import com.paysi.collaborator.port.CollaboratorRepository;
import com.paysi.core.error.ConflictException;
import com.paysi.core.error.NotFoundException;
import com.paysi.core.error.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CollaboratorServiceTest {
    private static final UUID OWNER = UUID.randomUUID();
    private static final UUID ID = UUID.randomUUID();
    private static final Collaborator PENDING = new Collaborator(ID, "ana@exemplo.com", "PENDING", List.of("ALL"), Instant.now());

    private CollaboratorRepository repository;
    private CollaboratorMailSender mail;
    private CollaboratorService service;

    @BeforeEach
    void setUp() {
        repository = mock(CollaboratorRepository.class);
        mail = mock(CollaboratorMailSender.class);
        service = new CollaboratorService(repository, mail);
        when(repository.ownerEmail(OWNER)).thenReturn("dono@exemplo.com");
        when(repository.ownerName(OWNER)).thenReturn("Dono");
    }

    @Test
    void addsNormalizesEmailAndSendsTheInvite() {
        when(repository.insert(OWNER, "ana@exemplo.com", List.of("ALL"))).thenReturn(PENDING);
        assertThat(service.add(OWNER, "  Ana@Exemplo.com ", List.of("ALL", "sales"))).isEqualTo(PENDING);
        verify(mail).sendInvite("ana@exemplo.com", "Dono");
    }

    @Test
    void refusesTheOwnerAndDuplicates() {
        assertThatThrownBy(() -> service.add(OWNER, "DONO@exemplo.com", List.of("ALL"))).isInstanceOf(ConflictException.class);
        when(repository.exists(OWNER, "ana@exemplo.com")).thenReturn(true);
        assertThatThrownBy(() -> service.add(OWNER, "ana@exemplo.com", List.of("ALL"))).isInstanceOf(ConflictException.class);
        verify(repository, never()).insert(any(), anyString(), any());
    }

    @Test
    void validatesEmailAndPermissions() {
        assertThatThrownBy(() -> service.add(OWNER, "sem-arroba", List.of("ALL"))).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> service.add(OWNER, "a@b.com", List.of())).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> service.add(OWNER, "a@b.com", List.of("admin"))).isInstanceOf(ValidationException.class);
        assertThat(CollaboratorService.normalizePermissions(List.of("sales", "sales", "finance"))).containsExactly("sales", "finance");
    }

    @Test
    void aFailedEmailDoesNotLoseTheInvite() {
        when(repository.insert(OWNER, "ana@exemplo.com", List.of("ALL"))).thenReturn(PENDING);
        doThrow(new RuntimeException("smtp")).when(mail).sendInvite(anyString(), anyString());
        assertThat(service.add(OWNER, "ana@exemplo.com", List.of("ALL"))).isEqualTo(PENDING);
    }

    @Test
    void resendOnlyForPendingAndRemoveNeedsAnExistingRow() {
        when(repository.find(OWNER, ID)).thenReturn(Optional.of(PENDING));
        service.resend(OWNER, ID);
        verify(repository).touchInvite(OWNER, ID);
        verify(mail).sendInvite("ana@exemplo.com", "Dono");
        when(repository.find(OWNER, ID)).thenReturn(Optional.of(new Collaborator(ID, "ana@exemplo.com", "ACTIVE", List.of("ALL"), Instant.now())));
        assertThatThrownBy(() -> service.resend(OWNER, ID)).isInstanceOf(ConflictException.class);
        when(repository.find(OWNER, ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.remove(OWNER, ID)).isInstanceOf(NotFoundException.class);
        service.list(OWNER, "  ", 5);
        verify(repository).count(OWNER, null);
    }
}
