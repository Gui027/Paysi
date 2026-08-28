package com.paysi.admin.port;

import com.paysi.admin.domain.*;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface AdminRepository {
    Optional<Credential> credential(String email);
    List<AdminSearchResult> search(String query, int limit);
    Optional<TargetState> lockTarget(String type, UUID id);
    void updateTargetStatus(String type, UUID id, String status);
    void audit(UUID adminId, String action, String targetType, String targetId, String reason,
               String beforeState, String afterState);
    void insertAdjustment(UUID id, AdjustmentCommand command, UUID requestedBy, boolean autoApproved);
    Optional<StoredAdjustment> lockAdjustment(UUID id);
    void approveAdjustment(UUID id, UUID approvedBy);
    void markAdjustmentApplied(UUID id);

    record Credential(UUID id, String role, String passwordHash, byte[] mfaSecretEncrypted) {}
    record TargetState(String type, UUID id, String status) {}
    record StoredAdjustment(UUID id, AdjustmentCommand command, UUID requestedBy,
                            UUID approvedBy, boolean autoApproved, String status) {}

    static Set<String> allowedStatuses(String type) {
        return switch (type) {
            case "ACCOUNT" -> Set.of("ACTIVE", "LIMITED", "SUSPENDED", "CLOSED");
            case "ORDER" -> Set.of("FAILED", "EXPIRED");
            case "SUBSCRIPTION" -> Set.of("PAST_DUE", "CANCELED");
            default -> Set.of();
        };
    }
}
