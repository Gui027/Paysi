package com.paysi.payment.inbox.web;

import com.paysi.payment.inbox.app.ProviderEventService;
import com.paysi.payment.inbox.domain.ProviderEventResult;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/payment/provider-events")
public class ProviderEventController {
    private final ProviderEventService service;

    public ProviderEventController(ProviderEventService service) {
        this.service = service;
    }

    @PostMapping("/{provider}")
    public ResponseEntity<ProviderEventResult> receive(@PathVariable String provider,
                                                        @RequestHeader(value = "X-Provider-Signature",
                                                                required = false) String signature,
                                                        @RequestHeader(value = "asaas-access-token",
                                                                required = false) String asaasAccessToken,
                                                        @RequestBody String payload) {
        String token = asaasAccessToken != null ? asaasAccessToken : signature;
        return ResponseEntity.accepted().body(service.handle(provider, payload, token));
    }
}
