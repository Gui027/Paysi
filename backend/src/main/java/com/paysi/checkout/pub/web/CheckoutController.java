package com.paysi.checkout.pub.web;

import com.paysi.checkout.pub.app.CheckoutContractService;
import com.paysi.checkout.pub.web.dto.CheckoutResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequestMapping("/v1")
@Tag(name = "Checkout público")
public class CheckoutController {
    private final CheckoutContractService contracts;

    public CheckoutController(CheckoutContractService contracts) {
        this.contracts = contracts;
    }

    @GetMapping("/offers/{slug}/checkout")
    @Operation(summary = "Carregar contrato público de checkout")
    public ResponseEntity<CheckoutResponse> get(@PathVariable String slug) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofSeconds(30)).cachePublic())
                .body(CheckoutResponse.from(contracts.get(slug)));
    }
}
