package com.paysi.checkout.order.web;

import com.paysi.checkout.order.app.CreateOrderService;
import com.paysi.checkout.order.app.OrderResult;
import com.paysi.checkout.order.web.dto.CreateOrderRequest;
import com.paysi.checkout.order.web.dto.OrderResponse;
import com.paysi.security.ratelimit.app.CheckoutRateLimitGuard;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1")
@Tag(name = "Checkout público")
public class CheckoutOrderController {
    private final CreateOrderService orders;
    private final CheckoutRateLimitGuard rateLimit;

    public CheckoutOrderController(CreateOrderService orders, CheckoutRateLimitGuard rateLimit) {
        this.orders = orders;
        this.rateLimit = rateLimit;
    }

    @PostMapping("/checkout/{slug}/orders")
    @Operation(summary = "Criar pedido do checkout público",
            description = "Exige Idempotency-Key. A mesma chave com o mesmo corpo devolve o "
                    + "pedido original com 200; com corpo diferente devolve 409. Nenhum valor "
                    + "é aceito do cliente: preço e desconto são relidos do banco. Limitado por "
                    + "IP, CPF/CNPJ e impressão de dispositivo (AM-05, AM-22).")
    public ResponseEntity<OrderResponse> create(@PathVariable String slug,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateOrderRequest request, HttpServletRequest httpRequest) {
        rateLimit.checkOrderAttempt(clientIp(httpRequest), request.buyer().taxId(), request.visitorKey());
        OrderResult result = orders.create(slug, idempotencyKey, request.toCommand());
        return ResponseEntity.status(result.replay() ? HttpStatus.OK : HttpStatus.CREATED)
                .body(OrderResponse.from(result.order()));
    }

    /**
     * Primeiro IP de {@code X-Forwarded-For} quando presente (atrás de proxy/CDN),
     * senão o IP da conexão direta.
     */
    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
