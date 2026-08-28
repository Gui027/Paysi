package com.paysi.admin.app;

import com.paysi.admin.domain.*;
import com.paysi.admin.port.AdminRepository;
import com.paysi.core.error.ConflictException;
import com.paysi.core.error.ValidationException;
import com.paysi.ledger.app.LedgerService;
import com.paysi.ledger.app.LedgerWriteResult;
import com.paysi.ledger.domain.*;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AdminServiceTest {
    private static final UUID REQUESTER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID APPROVER = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID ACCOUNT = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID ADJUSTMENT = UUID.fromString("44444444-4444-4444-4444-444444444444");

    @Test
    void autoApprovesOnlyBelowLimitAndWritesBalancedLedger() {
        var fixture = fixture();
        var result = fixture.service.requestAdjustment(principal(REQUESTER), command(9_999));

        assertThat(result.status()).isEqualTo("APPLIED");
        assertThat(result.autoApproved()).isTrue();
        verify(fixture.repository).insertAdjustment(any(), any(), eq(REQUESTER), eq(true));
        var ledgerCommand = org.mockito.ArgumentCaptor.forClass(LedgerCommand.class);
        verify(fixture.ledger).write(ledgerCommand.capture());
        assertThat(ledgerCommand.getValue().entries()).hasSize(2);
        assertThat(ledgerCommand.getValue().entries().stream().mapToLong(entry ->
                entry.direction() == Direction.CREDIT ? entry.amountCents() : -entry.amountCents()).sum())
                .isZero();
    }

    @Test
    void highValueWaitsForAnotherAdminAndRequesterCannotApprove() {
        var fixture = fixture();
        assertThat(fixture.service.requestAdjustment(principal(REQUESTER), command(10_000)).status())
                .isEqualTo("PENDING_APPROVAL");
        verifyNoInteractions(fixture.ledger);

        when(fixture.repository.lockAdjustment(ADJUSTMENT)).thenReturn(Optional.of(
                new AdminRepository.StoredAdjustment(ADJUSTMENT, command(10_000), REQUESTER,
                        null, false, "PENDING_APPROVAL")));
        assertThatThrownBy(() -> fixture.service.approveAdjustment(
                principal(REQUESTER), ADJUSTMENT, "revisado"))
                .isInstanceOf(ConflictException.class);

        var result = fixture.service.approveAdjustment(principal(APPROVER), ADJUSTMENT, "revisado");
        assertThat(result.status()).isEqualTo("APPLIED");
        verify(fixture.repository).approveAdjustment(ADJUSTMENT, APPROVER);
        verify(fixture.repository).markAdjustmentApplied(ADJUSTMENT);
    }

    @Test
    void statusChangeRequiresReasonAndCreatesAppendOnlyAuditEntry() {
        var fixture = fixture();
        assertThatThrownBy(() -> fixture.service.changeStatus(
                principal(REQUESTER), "account", ACCOUNT, "SUSPENDED", " "))
                .isInstanceOf(ValidationException.class);

        when(fixture.repository.lockTarget("ACCOUNT", ACCOUNT)).thenReturn(Optional.of(
                new AdminRepository.TargetState("ACCOUNT", ACCOUNT, "ACTIVE")));
        fixture.service.changeStatus(principal(REQUESTER), "account", ACCOUNT,
                "SUSPENDED", "análise de risco");
        verify(fixture.repository).updateTargetStatus("ACCOUNT", ACCOUNT, "SUSPENDED");
        verify(fixture.repository).audit(REQUESTER, "STATUS_CHANGED", "ACCOUNT",
                ACCOUNT.toString(), "análise de risco", "{\"status\":\"ACTIVE\"}",
                "{\"status\":\"SUSPENDED\"}");
    }

    private static Fixture fixture() {
        var repository = mock(AdminRepository.class);
        var ledger = mock(LedgerService.class);
        when(ledger.write(any())).thenReturn(new LedgerWriteResult(ADJUSTMENT, false));
        return new Fixture(new AdminService(repository, ledger, 10_000), repository, ledger);
    }

    private static AdjustmentCommand command(long amount) {
        return new AdjustmentCommand(ACCOUNT, Bucket.AVAILABLE, Direction.CREDIT,
                amount, "support-123", "correção operacional");
    }

    private static AdminPrincipal principal(UUID id) {
        return new AdminPrincipal(id, "RISK");
    }

    private record Fixture(AdminService service, AdminRepository repository, LedgerService ledger) {}
}
