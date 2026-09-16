package com.paysi.catalog.coupon.app;

import com.paysi.catalog.coupon.domain.Coupon;
import com.paysi.catalog.coupon.port.CouponRepository;
import com.paysi.core.error.ConflictException;
import com.paysi.core.error.NotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/**
 * Aplicação de cupom no checkout, em três tempos: {@link #quote} para a simulação,
 * que não escreve nada; {@link #reserve} para a criação do pedido, que decide a
 * disponibilidade no banco; e {@link #confirm} para a trilha, que só roda depois que
 * o pedido existe — {@code coupon_redemptions.order_id} tem chave estrangeira para
 * {@code orders}.
 *
 * <p>A ordem entre reservar e conferir o limite por comprador é a correção, não um
 * detalhe: conferir primeiro e incrementar depois reintroduz exatamente a corrida que
 * o UPDATE condicional resolve (documento 6, §7).
 *
 * <p>Validação de piso é na criação do cupom, não aqui (documento 1, §3.3).
 */
@Service
public class CouponRedemptionService {
    private final CouponRepository coupons;
    private final Clock clock;

    @Autowired
    public CouponRedemptionService(CouponRepository coupons) {
        this(coupons, Clock.systemUTC());
    }

    CouponRedemptionService(CouponRepository coupons, Clock clock) {
        this.coupons = coupons;
        this.clock = clock;
    }

    /** Desconto que o cupom daria agora, sem consumir unidade. */
    @Transactional(readOnly = true)
    public CouponDiscount quote(UUID offerId, String code, long grossCents) {
        if (blank(code)) return CouponDiscount.none();
        return discountOf(applicable(offerId, code), grossCents);
    }

    /**
     * Consome uma unidade. Deve rodar dentro da transação que cria o pedido e
     * <em>depois</em> que o pedido foi efetivamente gravado: uma requisição repetida
     * não pode gastar unidade de cupom.
     */
    @Transactional
    public void reserveUnit(CouponDiscount discount) {
        if (!discount.present()) return;
        if (!coupons.reserve(discount.couponId(), clock.instant())) {
            // Esgotou, venceu ou perdeu a corrida entre a leitura e o UPDATE.
            throw exhausted();
        }
    }

    /**
     * Grava a trilha do resgate e só então confere o limite por comprador. Estourado,
     * a exceção desfaz a transação inteira — incremento, resgate e pedido.
     */
    @Transactional
    public void confirm(CouponDiscount discount, UUID orderId, UUID buyerId) {
        if (!discount.present()) return;
        coupons.recordRedemption(discount.couponId(), orderId, buyerId, discount.discountCents());
        if (coupons.countRedemptionsByBuyer(discount.couponId(), buyerId) > discount.maxPerBuyer()) {
            throw new ConflictException("COUPON_LIMIT_REACHED",
                    "Este cupom já foi usado o número máximo de vezes por este comprador", "coupon");
        }
    }

    private Coupon applicable(UUID offerId, String code) {
        return coupons.findApplicable(offerId, normalize(code))
                .orElseThrow(CouponRedemptionService::notFound);
    }

    private CouponDiscount discountOf(Coupon coupon, long grossCents) {
        Instant now = clock.instant();
        if (coupon.startsAt() != null && now.isBefore(coupon.startsAt())) {
            throw new ConflictException("COUPON_NOT_STARTED",
                    "Este cupom ainda não está valendo", "coupon");
        }
        if (coupon.expiresAt() != null && !now.isBefore(coupon.expiresAt())) {
            throw new ConflictException("COUPON_EXPIRED", "Este cupom está vencido", "coupon");
        }
        if (coupon.maxRedemptions() != null && coupon.redeemedCount() >= coupon.maxRedemptions()) {
            throw exhausted();
        }
        return new CouponDiscount(coupon.id(), coupon.code(),
                coupon.discountCents(grossCents), coupon.maxPerBuyer());
    }

    private static boolean blank(String code) {
        return code == null || code.isBlank();
    }

    private static String normalize(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private static ConflictException exhausted() {
        return new ConflictException("COUPON_EXHAUSTED", "Este cupom já foi todo utilizado", "coupon");
    }

    private static NotFoundException notFound() {
        return new NotFoundException("COUPON_NOT_FOUND", "Cupom não encontrado para esta oferta");
    }
}
