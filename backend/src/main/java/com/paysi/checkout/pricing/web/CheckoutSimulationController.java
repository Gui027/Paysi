package com.paysi.checkout.pricing.web;

import com.paysi.checkout.pricing.app.PriceSimulationService;
import com.paysi.checkout.pricing.web.dto.SimulationRequest;
import com.paysi.checkout.pricing.web.dto.SimulationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1")
@Tag(name = "Checkout público")
public class CheckoutSimulationController {
    private final PriceSimulationService prices;

    public CheckoutSimulationController(PriceSimulationService prices) {
        this.prices = prices;
    }

    @PostMapping("/checkout/{slug}/simulation")
    @Operation(summary = "Simular preço com meio de pagamento, parcelas e cupom",
            description = "Não consome unidade do cupom e não persiste nada. Devolve a mesma "
                    + "memória de cálculo que a criação do pedido usará.")
    public SimulationResponse simulate(@PathVariable String slug,
            @Valid @RequestBody SimulationRequest request) {
        return SimulationResponse.from(prices.simulate(slug, request.method(),
                request.installmentsOrOne(), request.coupon()));
    }
}
