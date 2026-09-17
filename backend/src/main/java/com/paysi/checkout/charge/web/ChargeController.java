package com.paysi.checkout.charge.web;

import com.paysi.checkout.charge.app.ChargeCreationService;
import com.paysi.checkout.charge.app.StartChargeCommand;
import com.paysi.checkout.charge.web.dto.ChargeStartRequest;
import com.paysi.checkout.charge.web.dto.ChargeStartResponse;
import com.paysi.checkout.charge.web.dto.ChargeStatusResponse;
import com.paysi.checkout.charge.web.dto.ThreeDsConfirmRequest;
import com.paysi.payment.card.app.CardPaymentService;
import com.paysi.payment.card.domain.SaleEvidenceCommand;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * BE-07.1: recebe a intenção de pagar um pedido já criado (BE-05.2) e despacha para
 * o meio de pagamento resolvido no próprio pedido — o cliente não escolhe de novo.
 */
@RestController
@Tag(name = "Checkout público")
public class ChargeController {
    private final ChargeCreationService charges;
    private final CardPaymentService cardPayments;

    public ChargeController(ChargeCreationService charges, CardPaymentService cardPayments) {
        this.charges = charges;
        this.cardPayments = cardPayments;
    }

    @PostMapping("/v1/orders/{orderId}/charge")
    @Operation(summary = "Iniciar a cobrança de um pedido (cartão, Pix ou boleto conforme o pedido)")
    public ChargeStartResponse start(@PathVariable UUID orderId, @Valid @RequestBody ChargeStartRequest request,
                                      HttpServletRequest httpRequest) {
        var command = new StartChargeCommand(request.cardToken(), httpRequest.getRemoteAddr(),
                httpRequest.getHeader("User-Agent"), request.deviceKey(), request.termsHash(),
                request.termsAcceptedAt());
        return ChargeStartResponse.from(charges.start(orderId, command));
    }

    @PostMapping("/v1/charges/{chargeId}/confirm-3ds")
    @Operation(summary = "Confirmar o desafio 3DS de uma cobrança em cartão")
    public ChargeStartResponse confirmThreeDs(@PathVariable UUID chargeId,
                                               @Valid @RequestBody ThreeDsConfirmRequest request,
                                               HttpServletRequest httpRequest) {
        var evidence = new SaleEvidenceCommand(httpRequest.getRemoteAddr(), httpRequest.getHeader("User-Agent"),
                request.deviceKey(), request.termsHash(), request.termsAcceptedAt());
        var result = cardPayments.confirmThreeDs(chargeId, request.challengeToken(), evidence);
        return ChargeStartResponse.fromCard(chargeId, result);
    }

    @GetMapping("/v1/charges/{chargeId}")
    @Operation(summary = "Consultar status da cobrança (uso: polling de Pix/boleto)")
    public ChargeStatusResponse status(@PathVariable UUID chargeId) {
        return ChargeStatusResponse.from(charges.view(chargeId));
    }
}
