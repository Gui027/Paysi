package com.paysi.checkout.refund.web;

import com.paysi.checkout.refund.app.RefundCommand;
import com.paysi.checkout.refund.app.RefundResult;
import com.paysi.checkout.refund.app.RefundService;
import com.paysi.identity.session.app.SessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** BE-12.1: reembolso total ou parcial de uma cobrança, sempre pelo vendedor dono dela. */
@RestController
@Tag(name = "Cobranças")
public class RefundController {
    private static final String SESSION_COOKIE = "paysi_session";

    private final RefundService refunds;
    private final SessionService sessions;

    public RefundController(RefundService refunds, SessionService sessions) {
        this.refunds = refunds;
        this.sessions = sessions;
    }

    @PostMapping("/v1/charges/{chargeId}/refunds")
    @Operation(summary = "Reembolsar uma cobrança, total ou parcialmente")
    public ResponseEntity<RefundResponse> refund(
            @CookieValue(name = SESSION_COOKIE, required = false) String token,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @PathVariable UUID chargeId,
            @RequestBody RefundRequest request) {
        UUID sellerId = sessions.authenticate(token).session().accountId();
        var result = refunds.refund(sellerId, chargeId,
                new RefundCommand(request.amountCents(), request.reason(), idempotencyKey));
        HttpStatus status = result.idempotentReplay() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(RefundResponse.from(result));
    }

    public record RefundRequest(Long amountCents, String reason) {
    }

    public record RefundResponse(UUID refundId, String status, long sellerCents, long affiliateCents,
                                  long platformCents, long providerCents, long chargeRefundedCents,
                                  String chargeStatus) {
        static RefundResponse from(RefundResult result) {
            return new RefundResponse(result.refundId(), result.status(), result.sellerCents(),
                    result.affiliateCents(), result.platformCents(), result.providerCents(),
                    result.chargeRefundedCents(), result.chargeStatus());
        }
    }
}
