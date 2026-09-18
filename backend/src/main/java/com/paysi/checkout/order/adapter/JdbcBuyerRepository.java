package com.paysi.checkout.order.adapter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paysi.checkout.order.domain.Buyer;
import com.paysi.checkout.order.domain.BuyerAddress;
import com.paysi.checkout.order.port.BuyerRepository;
import com.paysi.identity.domain.PersonType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
class JdbcBuyerRepository implements BuyerRepository {
    private static final String SELECT = """
            SELECT * FROM buyers
             WHERE tax_id = ? AND email = ? AND anonymized_at IS NULL
            """;

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    JdbcBuyerRepository(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public Optional<Buyer> findActive(String taxId, String email) {
        return jdbc.query(SELECT, (rs, row) -> map(rs), taxId, email).stream().findFirst();
    }

    @Override
    public Buyer insertOrRead(Buyer buyer, Instant createdAt) {
        // O índice único parcial (tax_id, email) decide quem cria; o perdedor relê.
        jdbc.update("""
                INSERT INTO buyers
                  (id, email, tax_id, person_type, name, legal_name, municipal_reg, address, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, cast(? as jsonb), ?)
                ON CONFLICT (tax_id, email) WHERE anonymized_at IS NULL DO NOTHING
                """, buyer.id(), buyer.email(), buyer.taxId(), buyer.personType().name(),
                buyer.name(), buyer.legalName(), buyer.municipalReg(), addressJson(buyer.address()),
                Timestamp.from(createdAt));

        return findActive(buyer.taxId(), buyer.email()).orElseThrow(() ->
                new IllegalStateException("Comprador desapareceu logo após a gravação"));
    }

    @Override
    public boolean exists(UUID id) {
        return !jdbc.query("SELECT 1 FROM buyers WHERE id = ?", (rs, row) -> 1, id).isEmpty();
    }

    @Override
    public boolean anonymize(UUID id, Instant anonymizedAt) {
        // Idempotente: anonymized_at IS NULL na condição faz a segunda chamada não
        // sobrescrever o carimbo original nem gerar linhas afetadas de novo.
        int rows = jdbc.update("""
                UPDATE buyers
                   SET name = 'Titular anonimizado', email = ('anon-' || id || '@anonimizado.paysi')::citext,
                       tax_id = '00000000000', legal_name = NULL, municipal_reg = NULL, address = NULL,
                       anonymized_at = ?
                 WHERE id = ? AND anonymized_at IS NULL
                """, Timestamp.from(anonymizedAt), id);
        return rows > 0;
    }

    private Buyer map(ResultSet rs) throws SQLException {
        return new Buyer(rs.getObject("id", UUID.class), rs.getString("name"),
                rs.getString("email"), PersonType.valueOf(rs.getString("person_type")),
                rs.getString("tax_id"), rs.getString("legal_name"), rs.getString("municipal_reg"),
                address(rs.getString("address")));
    }

    private BuyerAddress address(String raw) {
        if (raw == null) return null;
        try {
            return json.readValue(raw, BuyerAddress.class);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Endereço do comprador ilegível", error);
        }
    }

    private String addressJson(BuyerAddress address) {
        if (address == null) return null;
        try {
            return json.writeValueAsString(address);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Não foi possível gravar o endereço", error);
        }
    }
}
