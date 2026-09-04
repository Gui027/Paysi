package com.paysi.catalog.coupon.web;

import com.paysi.catalog.coupon.app.CouponService;
import com.paysi.catalog.coupon.web.dto.CouponRequest;
import com.paysi.catalog.coupon.web.dto.CouponResponse;
import com.paysi.identity.session.app.SessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/coupons")
@Tag(name = "Cupons")
public class CouponController {
    private static final String COOKIE_NAME = "paysi_session";

    private final CouponService coupons;
    private final SessionService sessions;

    public CouponController(CouponService coupons, SessionService sessions) {
        this.coupons = coupons;
        this.sessions = sessions;
    }

    @PostMapping
    @Operation(summary = "Criar cupom")
    public ResponseEntity<CouponResponse> create(
            @CookieValue(name = COOKIE_NAME, required = false) String token,
            @Valid @RequestBody CouponRequest request) {
        var created = coupons.create(accountId(token), request.code(), request.toValues());
        return ResponseEntity.created(URI.create("/v1/coupons/" + created.id()))
                .body(CouponResponse.from(created));
    }

    @GetMapping
    @Operation(summary = "Listar cupons")
    public List<CouponResponse> list(@CookieValue(name = COOKIE_NAME, required = false) String token) {
        return coupons.list(accountId(token)).stream().map(CouponResponse::from).toList();
    }

    @GetMapping("/{couponId}")
    @Operation(summary = "Detalhar cupom")
    public CouponResponse detail(
            @CookieValue(name = COOKIE_NAME, required = false) String token,
            @PathVariable UUID couponId) {
        return CouponResponse.from(coupons.get(accountId(token), couponId));
    }

    @PutMapping("/{couponId}")
    @Operation(summary = "Editar cupom")
    public CouponResponse update(
            @CookieValue(name = COOKIE_NAME, required = false) String token,
            @PathVariable UUID couponId, @Valid @RequestBody CouponRequest request) {
        return CouponResponse.from(coupons.update(accountId(token), couponId, request.toValues()));
    }

    @DeleteMapping("/{couponId}")
    @Operation(summary = "Arquivar cupom")
    public ResponseEntity<Void> archive(
            @CookieValue(name = COOKIE_NAME, required = false) String token,
            @PathVariable UUID couponId) {
        coupons.archive(accountId(token), couponId);
        return ResponseEntity.noContent().build();
    }

    private UUID accountId(String token) {
        return sessions.authenticate(token).session().accountId();
    }
}
