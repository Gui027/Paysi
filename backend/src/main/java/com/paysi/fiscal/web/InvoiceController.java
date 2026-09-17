package com.paysi.fiscal.web;

import com.paysi.core.error.NotFoundException;
import com.paysi.fiscal.port.InvoiceRepository;
import com.paysi.identity.session.app.SessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** GET /v1/invoices/{chargeId}: situação e link da nota fiscal (docs/02 §4.2). */
@RestController
@Tag(name = "Fiscal")
public class InvoiceController {
    private static final String SESSION_COOKIE = "paysi_session";

    private final InvoiceRepository invoices;
    private final SessionService sessions;

    public InvoiceController(InvoiceRepository invoices, SessionService sessions) {
        this.invoices = invoices;
        this.sessions = sessions;
    }

    @GetMapping("/v1/invoices/{chargeId}")
    @Operation(summary = "Situação e link da nota fiscal de uma cobrança")
    public ResponseEntity<InvoiceResponse> get(
            @CookieValue(name = SESSION_COOKIE, required = false) String token,
            @PathVariable UUID chargeId) {
        var sellerId = sessions.authenticate(token).session().accountId();
        var invoice = invoices.findByChargeForSeller(sellerId, chargeId)
                .orElseThrow(() -> new NotFoundException("INVOICE_NOT_FOUND", "Nota fiscal não encontrada"));
        return ResponseEntity.ok(InvoiceResponse.from(invoice));
    }

    public record InvoiceResponse(UUID chargeId, String status, String number, String pdfUrl, int attemptCount,
                                   String error) {
        static InvoiceResponse from(InvoiceRepository.InvoiceView view) {
            return new InvoiceResponse(view.chargeId(), view.status(), view.number(), view.pdfUrl(),
                    view.attemptCount(), view.error());
        }
    }
}
