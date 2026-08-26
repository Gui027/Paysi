package com.paysi.identity.kyc.adapter;

import com.paysi.identity.kyc.domain.KycProcess;
import com.paysi.identity.kyc.domain.KycRequirement;
import com.paysi.identity.kyc.port.KycStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcKycStore implements KycStore {
    private final JdbcTemplate jdbc;
    public JdbcKycStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public void lockAccount(UUID accountId) {
        jdbc.query("select pg_advisory_xact_lock(hashtextextended(?::text, 0))", resultSet -> null, accountId);
    }

    @Override
    public Optional<KycProcess> findProcess(UUID accountId) {
        return jdbc.query("select provider_process_id, provider_url, expires_at from kyc_processes where account_id = ?",
                (rs, row) -> new KycProcess(rs.getString(1), rs.getString(2), rs.getTimestamp(3).toInstant(), requirements(accountId)), accountId)
                .stream().findFirst();
    }

    @Override
    public List<KycRequirement> requirements(UUID accountId) {
        return jdbc.query("select code, label, status, reason, estimated_at from kyc_requirements where account_id = ? order by code",
                (rs, row) -> new KycRequirement(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), nullableInstant(rs.getTimestamp(5))), accountId);
    }

    @Override
    public void saveStarted(UUID accountId, KycProcess process) {
        jdbc.update("insert into kyc_processes(account_id, provider_process_id, provider_url, expires_at) values (?,?,?,?) " +
                        "on conflict (account_id) do update set provider_process_id=excluded.provider_process_id, provider_url=excluded.provider_url, expires_at=excluded.expires_at, updated_at=now()",
                accountId, process.providerProcessId(), process.providerUrl(), Timestamp.from(process.expiresAt()));
        jdbc.update("delete from kyc_requirements where account_id = ?", accountId);
        process.requirements().forEach(requirement -> jdbc.update(
                "insert into kyc_requirements(account_id,code,label,status,reason,estimated_at) values (?,?,?,?,?,?)",
                accountId, requirement.code(), requirement.label(), requirement.status(), requirement.reason(),
                requirement.estimatedAt() == null ? null : Timestamp.from(requirement.estimatedAt())));
        jdbc.update("update accounts set kyc_status = 'SUBMITTED' where id = ? and kyc_status in ('PENDING','REJECTED')", accountId);
    }

    private static Instant nullableInstant(Timestamp timestamp) { return timestamp == null ? null : timestamp.toInstant(); }
}
