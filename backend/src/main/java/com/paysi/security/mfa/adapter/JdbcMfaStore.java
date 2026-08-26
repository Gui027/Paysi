package com.paysi.security.mfa.adapter;

import com.paysi.security.mfa.domain.MfaChallenge;
import com.paysi.security.mfa.domain.MfaCredential;
import com.paysi.security.mfa.domain.SensitiveOperation;
import com.paysi.security.mfa.port.MfaStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcMfaStore implements MfaStore {
    private final JdbcTemplate jdbc;
    public JdbcMfaStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override public Optional<MfaCredential> credential(UUID accountId) {
        return jdbc.query("select secret_enc, confirmed_at from mfa_credentials where account_id=?",
                (rs, row) -> new MfaCredential(rs.getBytes(1), instant(rs.getTimestamp(2))), accountId).stream().findFirst();
    }

    @Override public void saveEnrollment(UUID accountId, byte[] secret, List<String> hashes) {
        jdbc.update("insert into mfa_credentials(account_id,secret_enc,confirmed_at) values (?,?,null) on conflict(account_id) do update set secret_enc=excluded.secret_enc,confirmed_at=null", accountId, secret);
        jdbc.update("delete from mfa_recovery_codes where account_id=?", accountId);
        hashes.forEach(hash -> jdbc.update("insert into mfa_recovery_codes(account_id,code_hash) values (?,?)", accountId, hash));
    }

    @Override public void enable(UUID accountId, Instant enabledAt) { jdbc.update("update mfa_credentials set confirmed_at=? where account_id=?", Timestamp.from(enabledAt), accountId); }
    @Override public boolean consumeRecoveryCode(UUID accountId, String hash) { return jdbc.update("delete from mfa_recovery_codes where account_id=? and code_hash=?", accountId, hash) == 1; }
    @Override public void saveChallenge(MfaChallenge c) { jdbc.update("insert into mfa_challenges(id,account_id,operation,expires_at) values (?,?,?,?)", c.id(), c.accountId(), c.operation().name(), Timestamp.from(c.expiresAt())); }
    @Override public Optional<MfaChallenge> lockChallenge(UUID id) {
        return jdbc.query("select id,account_id,operation,expires_at,attempts,verified_at,consumed_at from mfa_challenges where id=? for update",
                (rs,row) -> new MfaChallenge(rs.getObject(1,UUID.class),rs.getObject(2,UUID.class),SensitiveOperation.valueOf(rs.getString(3)),rs.getTimestamp(4).toInstant(),rs.getInt(5),instant(rs.getTimestamp(6)),instant(rs.getTimestamp(7))),id).stream().findFirst();
    }
    @Override public void incrementAttempts(UUID id) { jdbc.update("update mfa_challenges set attempts=attempts+1 where id=? and attempts<5", id); }
    @Override public void markVerified(UUID id, Instant at) { jdbc.update("update mfa_challenges set verified_at=? where id=?",Timestamp.from(at),id); }
    @Override public boolean consumeVerified(UUID id, UUID accountId, String operation, Instant now) {
        return jdbc.update("update mfa_challenges set consumed_at=? where id=? and account_id=? and operation=? and verified_at is not null and consumed_at is null and expires_at>?", Timestamp.from(now),id,accountId,operation,Timestamp.from(now)) == 1;
    }
    private static Instant instant(Timestamp value) { return value == null ? null : value.toInstant(); }
}
