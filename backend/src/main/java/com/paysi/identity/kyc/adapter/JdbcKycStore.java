package com.paysi.identity.kyc.adapter;

import com.paysi.identity.kyc.domain.KycProcess;
import com.paysi.identity.kyc.domain.KycRequirement;
import com.paysi.identity.kyc.port.KycStore;
import com.paysi.security.mfa.port.SecretProtector;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcKycStore implements KycStore {
    private final JdbcTemplate jdbc;
    private final SecretProtector secrets;

    public JdbcKycStore(JdbcTemplate jdbc, SecretProtector secrets) {
        this.jdbc = jdbc;
        this.secrets = secrets;
    }

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

    @Override
    public void attachProviderAccount(UUID accountId, String providerAccountId, String accessToken) {
        byte[] encrypted = accessToken == null ? null : secrets.encrypt(accessToken.getBytes(StandardCharsets.UTF_8));
        jdbc.update("""
                update accounts set provider_account_id = coalesce(provider_account_id, ?),
                                     provider_access_token_enc = coalesce(provider_access_token_enc, ?)
                 where id = ?
                """, providerAccountId, encrypted, accountId);
    }

    @Override
    public Optional<String> decryptedAccessToken(UUID accountId) {
        List<byte[]> rows = jdbc.query("select provider_access_token_enc from accounts where id = ?",
                (rs, row) -> rs.getBytes(1), accountId);
        byte[] encrypted = rows.isEmpty() ? null : rows.get(0);
        return encrypted == null ? Optional.empty() : Optional.of(new String(secrets.decrypt(encrypted), StandardCharsets.UTF_8));
    }

    @Override
    public com.paysi.identity.kyc.domain.ComplianceProfile complianceProfile(UUID accountId) {
        return jdbc.query("select postal_code, birth_date, income_value_cents from accounts where id = ?",
                (rs, row) -> new com.paysi.identity.kyc.domain.ComplianceProfile(rs.getString(1),
                        rs.getObject(2, java.time.LocalDate.class), rs.getObject(3, Long.class)), accountId)
                .stream().findFirst().orElse(new com.paysi.identity.kyc.domain.ComplianceProfile(null, null, null));
    }

    @Override
    public void saveComplianceProfile(UUID accountId, String postalCode, java.time.LocalDate birthDate, Long incomeValueCents) {
        jdbc.update("update accounts set postal_code = ?, birth_date = ?, income_value_cents = ? where id = ?",
                postalCode, birthDate == null ? null : java.sql.Date.valueOf(birthDate), incomeValueCents, accountId);
    }

    @Override
    public void clearProcess(UUID accountId) {
        jdbc.update("delete from kyc_requirements where account_id = ?", accountId);
        jdbc.update("delete from kyc_processes where account_id = ?", accountId);
    }

    @Override
    public void updateStatus(UUID accountId, com.paysi.identity.domain.KycStatus status, List<KycRequirement> requirements) {
        jdbc.update("update accounts set kyc_status = ? where id = ?", status.name(), accountId);
        jdbc.update("delete from kyc_requirements where account_id = ?", accountId);
        requirements.forEach(requirement -> jdbc.update(
                "insert into kyc_requirements(account_id,code,label,status,reason,estimated_at) values (?,?,?,?,?,?)",
                accountId, requirement.code(), requirement.label(), requirement.status(), requirement.reason(),
                requirement.estimatedAt() == null ? null : Timestamp.from(requirement.estimatedAt())));
    }

    private static Instant nullableInstant(Timestamp timestamp) { return timestamp == null ? null : timestamp.toInstant(); }
}
