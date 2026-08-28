package com.paysi.admin.adapter;

import com.paysi.admin.domain.*;
import com.paysi.admin.port.AdminRepository;
import com.paysi.ledger.domain.Bucket;
import com.paysi.ledger.domain.Direction;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcAdminRepository implements AdminRepository {
    private final JdbcTemplate jdbc;

    public JdbcAdminRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Credential> credential(String email) {
        return jdbc.query("""
                select a.id,a.role,a.password_hash,m.secret_enc
                  from admin_users a
                  join admin_mfa_credentials m on m.admin_id=a.id and m.confirmed_at is not null
                 where a.email=? and a.disabled_at is null and a.mfa_enforced
                """, (rs, row) -> new Credential(rs.getObject(1, UUID.class), rs.getString(2),
                rs.getString(3), rs.getBytes(4)), email).stream().findFirst();
    }

    @Override
    public List<AdminSearchResult> search(String query, int limit) {
        String pattern = "%" + query + "%";
        return jdbc.query("""
                select resource_type,id,status,summary from (
                  select 'ACCOUNT' resource_type,id,status,email::text summary,created_at
                    from accounts where id::text ilike ? or email::text ilike ?
                                      or full_name ilike ? or tax_id ilike ?
                  union all
                  select 'ORDER',id,status,'offer='||offer_id::text,created_at
                    from orders where id::text ilike ? or offer_id::text ilike ?
                  union all
                  select 'SUBSCRIPTION',id,status,'order='||order_id::text,created_at
                    from subscriptions where id::text ilike ? or order_id::text ilike ?
                ) found order by created_at desc limit ?
                """, (rs, row) -> new AdminSearchResult(rs.getString(1), rs.getObject(2, UUID.class),
                        rs.getString(3), rs.getString(4)),
                pattern, pattern, pattern, pattern, pattern, pattern, pattern, pattern, limit);
    }

    @Override
    public Optional<TargetState> lockTarget(String type, UUID id) {
        String table = table(type);
        return jdbc.query("select status from " + table + " where id=? for update",
                (rs, row) -> new TargetState(type, id, rs.getString(1)), id).stream().findFirst();
    }

    @Override
    public void updateTargetStatus(String type, UUID id, String status) {
        jdbc.update("update " + table(type) + " set status=? where id=?", status, id);
    }

    @Override
    public void audit(UUID adminId, String action, String targetType, String targetId, String reason,
                      String beforeState, String afterState) {
        jdbc.update("""
                insert into admin_audit_log(admin_id,action,target_type,target_id,reason,before_state,after_state)
                values (?,?,?,?,?,cast(? as jsonb),cast(? as jsonb))
                """, adminId, action, targetType, targetId, reason, beforeState, afterState);
    }

    @Override
    public void insertAdjustment(UUID id, AdjustmentCommand command, UUID requestedBy, boolean autoApproved) {
        jdbc.update("""
                insert into ledger_adjustments(id,kind,account_id,bucket,direction,amount_cents,reference_id,
                  reason,requested_by,approved_by,auto_approved,approved_at,status)
                values (?,'ADJUSTMENT',?,?,?,?,?,?,?,case when ? then ? else null end,?,
                  case when ? then now() else null end,case when ? then 'APPROVED' else 'PENDING_APPROVAL' end)
                """, id, command.accountId(), command.bucket().name(), command.direction().name(),
                command.amountCents(), command.reference(), command.reason(), requestedBy,
                autoApproved, requestedBy, autoApproved, autoApproved, autoApproved);
    }

    @Override
    public Optional<StoredAdjustment> lockAdjustment(UUID id) {
        return jdbc.query("""
                select id,account_id,bucket,direction,amount_cents,reference_id,reason,requested_by,
                       approved_by,auto_approved,status
                  from ledger_adjustments where id=? for update
                """, (rs, row) -> new StoredAdjustment(rs.getObject(1, UUID.class),
                new AdjustmentCommand(rs.getObject(2, UUID.class), Bucket.valueOf(rs.getString(3)),
                        Direction.valueOf(rs.getString(4)), rs.getLong(5), rs.getString(6), rs.getString(7)),
                rs.getObject(8, UUID.class), rs.getObject(9, UUID.class), rs.getBoolean(10), rs.getString(11)),
                id).stream().findFirst();
    }

    @Override
    public void approveAdjustment(UUID id, UUID approvedBy) {
        jdbc.update("""
                update ledger_adjustments set approved_by=?,approved_at=now(),status='APPROVED'
                 where id=? and status='PENDING_APPROVAL' and requested_by<>?
                """, approvedBy, id, approvedBy);
    }

    @Override
    public void markAdjustmentApplied(UUID id) {
        jdbc.update("update ledger_adjustments set status='APPLIED' where id=? and status='APPROVED'", id);
    }

    private static String table(String type) {
        return switch (type) {
            case "ACCOUNT" -> "accounts";
            case "ORDER" -> "orders";
            case "SUBSCRIPTION" -> "subscriptions";
            default -> throw new IllegalArgumentException("Tipo administrativo inválido");
        };
    }
}
