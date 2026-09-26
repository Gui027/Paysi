package com.paysi.collaborator.adapter;

import com.paysi.collaborator.app.CollaboratorModels.Collaborator;
import com.paysi.collaborator.port.CollaboratorRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Array;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class JdbcCollaboratorRepository implements CollaboratorRepository {
    private static final String COLUMNS = "id, email::text AS email, status, permissions, invited_at";
    private static final RowMapper<Collaborator> MAPPER = (rs, row) -> {
        Array permissions = rs.getArray("permissions");
        return new Collaborator(rs.getObject("id", UUID.class), rs.getString("email"), rs.getString("status"),
                List.of((String[]) permissions.getArray()), rs.getTimestamp("invited_at").toInstant());
    };

    private final JdbcTemplate jdbc;

    JdbcCollaboratorRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public String ownerEmail(UUID accountId) {
        return jdbc.queryForObject("SELECT email::text FROM accounts WHERE id = ?", String.class, accountId);
    }

    @Override
    public String ownerName(UUID accountId) {
        return jdbc.queryForObject("SELECT full_name FROM accounts WHERE id = ?", String.class, accountId);
    }

    @Override
    public boolean exists(UUID accountId, String email) {
        Integer found = jdbc.queryForObject("SELECT COUNT(*) FROM collaborators WHERE account_id = ? AND email = ?::citext",
                Integer.class, accountId, email);
        return found != null && found > 0;
    }

    @Override
    public Collaborator insert(UUID accountId, String email, List<String> permissions) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO collaborators (id, account_id, email, permissions) VALUES (?, ?, ?::citext, ?::text[])",
                id, accountId, email, array(permissions));
        return find(accountId, id).orElseThrow();
    }

    @Override
    public List<Collaborator> list(UUID accountId, String query, int limit, int offset) {
        List<Object> params = new ArrayList<>(List.of(accountId));
        String filter = filter(query, params);
        params.add(limit);
        params.add(offset);
        return jdbc.query("SELECT " + COLUMNS + " FROM collaborators WHERE account_id = ?" + filter
                + " ORDER BY invited_at DESC, id LIMIT ? OFFSET ?", MAPPER, params.toArray());
    }

    @Override
    public long count(UUID accountId, String query) {
        List<Object> params = new ArrayList<>(List.of(accountId));
        String filter = filter(query, params);
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM collaborators WHERE account_id = ?" + filter, Long.class, params.toArray());
        return total == null ? 0 : total;
    }

    @Override
    public Optional<Collaborator> find(UUID accountId, UUID id) {
        return jdbc.query("SELECT " + COLUMNS + " FROM collaborators WHERE account_id = ? AND id = ?", MAPPER, accountId, id)
                .stream().findFirst();
    }

    @Override
    public void updatePermissions(UUID accountId, UUID id, List<String> permissions) {
        jdbc.update("UPDATE collaborators SET permissions = ?::text[] WHERE account_id = ? AND id = ?", array(permissions), accountId, id);
    }

    @Override
    public void touchInvite(UUID accountId, UUID id) {
        jdbc.update("UPDATE collaborators SET invited_at = now() WHERE account_id = ? AND id = ?", accountId, id);
    }

    @Override
    public void delete(UUID accountId, UUID id) {
        jdbc.update("DELETE FROM collaborators WHERE account_id = ? AND id = ?", accountId, id);
    }

    private static String filter(String query, List<Object> params) {
        if (query == null) return "";
        params.add("%" + query.toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%");
        return " AND lower(email::text) LIKE ? ESCAPE '\\'";
    }

    /** Literal de array do Postgres; as áreas são valores fixos validados no serviço. */
    private static String array(List<String> values) {
        return "{" + String.join(",", values) + "}";
    }
}
