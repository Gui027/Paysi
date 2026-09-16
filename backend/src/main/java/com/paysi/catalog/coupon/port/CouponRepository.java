package com.paysi.catalog.coupon.port;

import com.paysi.catalog.coupon.domain.Coupon;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CouponRepository {
    void insert(Coupon coupon);

    List<Coupon> listActiveOwned(UUID sellerId);

    Optional<Coupon> findActiveOwned(UUID sellerId, UUID couponId);

    default Optional<Coupon> findApplicableOwned(UUID sellerId, UUID offerId, String code) {
        return listActiveOwned(sellerId).stream()
                .filter(coupon -> coupon.code().equals(code) && coupon.offerIds().contains(offerId))
                .findFirst();
    }

    /**
     * Busca o cupom aplicável a uma oferta sem escopo de vendedor: o checkout é anônimo
     * e o comprador informa apenas o código.
     */
    Optional<Coupon> findApplicable(UUID offerId, String code);

    /**
     * Incrementa {@code redeemed_count} com UPDATE condicional. É o próprio banco que
     * decide se ainda há unidade disponível, então cem chamadas simultâneas a um cupom
     * de cinquenta unidades resultam em exatamente cinquenta sucessos.
     *
     * @return {@code false} quando o cupom está arquivado, fora da janela de validade
     *         ou esgotado — nenhum desses casos incrementa o contador.
     */
    boolean reserve(UUID couponId, Instant now);

    /**
     * Trilha do resgate. Só pode ser chamada depois que o pedido existe, porque
     * {@code coupon_redemptions.order_id} tem chave estrangeira para {@code orders}.
     */
    void recordRedemption(UUID couponId, UUID orderId, UUID buyerId, long amountCents);

    /** Conferido <em>depois</em> da reserva, na mesma transação (documento 6, §7). */
    int countRedemptionsByBuyer(UUID couponId, UUID buyerId);

    void update(Coupon coupon);

    boolean archive(UUID sellerId, UUID couponId, Instant archivedAt);
}
