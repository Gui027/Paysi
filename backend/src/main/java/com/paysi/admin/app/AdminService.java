package com.paysi.admin.app;

import com.paysi.admin.domain.*;
import com.paysi.admin.port.AdminRepository;
import com.paysi.core.error.ConflictException;
import com.paysi.core.error.NotFoundException;
import com.paysi.core.error.ValidationException;
import com.paysi.ledger.app.LedgerService;
import com.paysi.ledger.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class AdminService {
    private static final UUID ADJUSTMENT_CLEARING =
            UUID.fromString("00000000-0000-0000-0000-0000000000a1");

    private final AdminRepository repository;
    private final LedgerService ledger;
    private final long autoApprovalLimit;

    public AdminService(AdminRepository repository, LedgerService ledger) {
        this(repository, ledger, 10_000);
    }

    AdminService(AdminRepository repository, LedgerService ledger, long limit) {
        this.repository = repository;
        this.ledger = ledger;
        this.autoApprovalLimit = limit;
    }

    public List<AdminSearchResult> search(String query) {
        if (query == null || query.isBlank()) {
            throw new ValidationException("ADMIN_QUERY_REQUIRED", "Informe o termo de busca", "query");
        }
        return repository.search(query.strip(), 50);
    }

    @Transactional
    public void changeStatus(AdminPrincipal actor, String type, UUID id, String status, String reason) {
        String normalizedType = type == null ? "" : type.toUpperCase();
        requireReason(reason);
        if (!AdminRepository.allowedStatuses(normalizedType).contains(status)) {
            throw new ValidationException("ADMIN_STATUS_INVALID", "Status inválido para o recurso", "status");
        }
        var before = repository.lockTarget(normalizedType, id)
                .orElseThrow(() -> new NotFoundException("ADMIN_TARGET_NOT_FOUND", "Recurso não encontrado"));
        repository.updateTargetStatus(normalizedType, id, status);
        repository.audit(actor.id(), "STATUS_CHANGED", normalizedType, id.toString(), reason,
                state(before.status()), state(status));
    }

    @Transactional
    public AdjustmentView requestAdjustment(AdminPrincipal actor, AdjustmentCommand command) {
        boolean autoApproved = command.amountCents() < autoApprovalLimit;
        UUID id = UUID.randomUUID();
        repository.insertAdjustment(id, command, actor.id(), autoApproved);
        repository.audit(actor.id(), "ADJUSTMENT_REQUESTED", "ADJUSTMENT", id.toString(),
                command.reason(), null, state(autoApproved ? "APPROVED" : "PENDING_APPROVAL"));
        if (autoApproved) apply(id, command);
        return new AdjustmentView(id, autoApproved ? "APPLIED" : "PENDING_APPROVAL", autoApproved);
    }

    @Transactional
    public AdjustmentView approveAdjustment(AdminPrincipal actor, UUID adjustmentId, String reason) {
        requireReason(reason);
        var adjustment = repository.lockAdjustment(adjustmentId)
                .orElseThrow(() -> new NotFoundException("ADJUSTMENT_NOT_FOUND", "Ajuste não encontrado"));
        if (!adjustment.status().equals("PENDING_APPROVAL")) {
            throw new ConflictException("ADJUSTMENT_NOT_PENDING", "Ajuste não aguarda aprovação", null);
        }
        if (adjustment.requestedBy().equals(actor.id())) {
            throw new ConflictException("ADJUSTMENT_SELF_APPROVAL", "Solicitante não pode aprovar", null);
        }
        repository.approveAdjustment(adjustmentId, actor.id());
        repository.audit(actor.id(), "ADJUSTMENT_APPROVED", "ADJUSTMENT", adjustmentId.toString(),
                reason, state("PENDING_APPROVAL"), state("APPROVED"));
        apply(adjustmentId, adjustment.command());
        return new AdjustmentView(adjustmentId, "APPLIED", false);
    }

    private void apply(UUID id, AdjustmentCommand command) {
        Direction opposite = command.direction() == Direction.CREDIT ? Direction.DEBIT : Direction.CREDIT;
        ledger.write(new LedgerCommand(TransactionType.ADJUSTMENT,
                new LedgerReference(ReferenceType.ADJUSTMENT, id.toString()), command.reason(),
                List.of(
                        new LedgerEntry(command.accountId(), command.bucket(), command.direction(),
                                command.amountCents(), Origin.OTHER, null),
                        new LedgerEntry(ADJUSTMENT_CLEARING, Bucket.SYSTEM, opposite,
                                command.amountCents(), Origin.OTHER, null))));
        repository.markAdjustmentApplied(id);
    }

    private static void requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new ValidationException("ADMIN_REASON_REQUIRED", "Motivo é obrigatório", "reason");
        }
    }

    private static String state(String status) {
        return "{\"status\":\"" + status + "\"}";
    }
}
