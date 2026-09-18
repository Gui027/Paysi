package com.paysi.compliance.lgpd.adapter;

import com.paysi.compliance.lgpd.domain.LgpdRequest;
import com.paysi.compliance.lgpd.port.LgpdRequestRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
class JdbcLgpdRequestRepository implements LgpdRequestRepository {
    private final JdbcTemplate jdbc;

    JdbcLgpdRequestRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public LgpdRequest insert(UUID id, String subjectKind, String subjectRef, String kind, Instant dueAt,
                               Instant createdAt) {
        jdbc.update("""
                INSERT INTO lgpd_requests (id, subject_kind, subject_ref, kind, status, due_at, created_at)
                VALUES (?, ?, ?, ?, 'OPEN', ?, ?)
                """, id, subjectKind, subjectRef, kind, Timestamp.from(dueAt), Timestamp.from(createdAt));
        return lockForUpdate(id).orElseThrow(() ->
                new IllegalStateException("Pedido LGPD desapareceu logo após a gravação"));
    }

    @Override
    public Optional<LgpdRequest> lockForUpdate(UUID id) {
        return jdbc.query("SELECT * FROM lgpd_requests WHERE id = ? FOR UPDATE", (rs, row) -> map(rs), id)
                .stream().findFirst();
    }

    @Override
    public void assign(UUID id, UUID assigneeId, String status) {
        jdbc.update("UPDATE lgpd_requests SET handled_by = ?, status = ? WHERE id = ?", assigneeId, status, id);
    }

    @Override
    public void resolve(UUID id, String status, String resolution) {
        jdbc.update("UPDATE lgpd_requests SET status = ?, resolution = ? WHERE id = ?", status, resolution, id);
    }

    private LgpdRequest map(ResultSet rs) throws SQLException {
        return new LgpdRequest(rs.getObject("id", UUID.class), rs.getString("subject_kind"),
                rs.getString("subject_ref"), rs.getString("kind"), rs.getString("status"),
                rs.getTimestamp("due_at").toInstant(), rs.getObject("handled_by", UUID.class),
                rs.getString("resolution"), rs.getTimestamp("created_at").toInstant());
    }
}
