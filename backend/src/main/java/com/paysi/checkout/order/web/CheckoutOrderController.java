package com.paysi.checkout.order.web;

import com.paysi.checkout.order.app.CreateOrderService;
import com.paysi.checkout.order.app.OrderResult;
import com.paysi.checkout.order.web.dto.CreateOrderRequest;
import com.paysi.checkout.order.web.dto.OrderResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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

    public CheckoutOrderController(CreateOrderService orders) {
        this.orders = orders;
    }

    @PostMapping("/checkout/{slug}/orders")
    @Operation(summary = "Criar pedido do checkout público",
            description = "Exige Idempotency-Key. A mesma chave com o mesmo corpo devolve o "
                    + "pedido original com 200; com corpo diferente devolve 409. Nenhum valor "
                    + "é aceito do cliente: preço e desconto são relidos do banco.")
    public ResponseEntity<OrderResponse> create(@PathVariable String slug,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateOrderRequest request) {
        OrderResult result = orders.create(slug, idempotencyKey, request.toCommand());
        return ResponseEntity.status(result.replay() ? HttpStatus.OK : HttpStatus.CREATED)
                .body(OrderResponse.from(result.order()));
    }
}
