package com.paysi.catalog.coupon.adapter;

import com.paysi.catalog.coupon.domain.Coupon;
import com.paysi.catalog.coupon.domain.CouponKind;
import com.paysi.catalog.coupon.port.CouponRepository;
import com.paysi.core.error.ConflictException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
class JdbcCouponRepository implements CouponRepository {
    private static final String SELECT = """
            SELECT * FROM coupons WHERE seller_id = ? AND archived_at IS NULL
            """;

    private final JdbcTemplate jdbc;

    JdbcCouponRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(Coupon coupon) {
        try {
            jdbc.update("""
                    INSERT INTO coupons
                      (id, seller_id, code, kind, value, starts_at, expires_at,
                       max_redemptions, max_per_buyer, redeemed_count, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, coupon.id(), coupon.sellerId(), coupon.code(), coupon.kind().name(),
                    coupon.value(), timestamp(coupon.startsAt()), timestamp(coupon.expiresAt()),
                    coupon.maxRedemptions(), coupon.maxPerBuyer(), coupon.redeemedCount(),
                    Timestamp.from(coupon.createdAt()));
        } catch (DataIntegrityViolationException error) {
            throw new ConflictException("COUPON_CODE_TAKEN",
                    "Já existe um cupom ativo com este código", "code");
        }
        replaceOffers(coupon);
    }

    @Override
    public List<Coupon> listActiveOwned(UUID sellerId) {
        return jdbc.query(SELECT + " ORDER BY created_at DESC, id DESC", (rs, row) -> map(rs), sellerId);
    }

    @Override
    public Optional<Coupon> findActiveOwned(UUID sellerId, UUID couponId) {
        return jdbc.query(SELECT + " AND id = ?", (rs, row) -> map(rs), sellerId, couponId)
                .stream().findFirst();
    }

    @Override
    public void update(Coupon coupon) {
        try {
            int changed = jdbc.update("""
                    UPDATE coupons
                       SET kind = ?, value = ?, starts_at = ?, expires_at = ?,
                           max_redemptions = ?, max_per_buyer = ?
                     WHERE id = ? AND archived_at IS NULL
                    """, coupon.kind().name(), coupon.value(), timestamp(coupon.startsAt()),
                    timestamp(coupon.expiresAt()), coupon.maxRedemptions(), coupon.maxPerBuyer(),
                    coupon.id());
            if (changed != 1) throw new IllegalStateException("Cupom desapareceu durante a atualização");
        } catch (DataIntegrityViolationException error) {
            throw new ConflictException("COUPON_INVALID", "Não foi possível salvar o cupom", null);
        }
        replaceOffers(coupon);
    }

    @Override
    public boolean archive(UUID sellerId, UUID couponId, Instant archivedAt) {
        return jdbc.update("""
                UPDATE coupons SET archived_at = ?
                 WHERE id = ? AND seller_id = ? AND archived_at IS NULL
                """, Timestamp.from(archivedAt), couponId, sellerId) == 1;
    }

    private Coupon map(ResultSet rs) throws SQLException {
        UUID id = rs.getObject("id", UUID.class);
        Integer maxRedemptions = (Integer) rs.getObject("max_redemptions");
        return new Coupon(id, rs.getObject("seller_id", UUID.class), rs.getString("code"),
                CouponKind.valueOf(rs.getString("kind")), rs.getInt("value"),
                instant(rs, "starts_at"), instant(rs, "expires_at"), maxRedemptions,
                rs.getInt("max_per_buyer"), rs.getInt("redeemed_count"), offerIds(id),
                instant(rs, "archived_at"), instant(rs, "created_at"));
    }

    private Set<UUID> offerIds(UUID couponId) {
        List<UUID> ids = jdbc.query("SELECT offer_id FROM coupon_offers WHERE coupon_id = ?",
                (rs, row) -> rs.getObject("offer_id", UUID.class), couponId);
        return ids.isEmpty() ? Set.of() : new HashSet<>(ids);
    }

    private void replaceOffers(Coupon coupon) {
        jdbc.update("DELETE FROM coupon_offers WHERE coupon_id = ?", coupon.id());
        jdbc.batchUpdate("INSERT INTO coupon_offers (coupon_id, offer_id) VALUES (?, ?)",
                coupon.offerIds(), coupon.offerIds().size(), (statement, offerId) -> {
                    statement.setObject(1, coupon.id());
                    statement.setObject(2, offerId);
                });
    }

    private static Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        var timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }
}
