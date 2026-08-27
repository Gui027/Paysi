package com.paysi.identity.kyc.webhook.web;

import com.paysi.identity.kyc.webhook.app.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/v1/webhooks/kyc")
public class KycWebhookController {
    private final KycWebhookService service;public KycWebhookController(KycWebhookService service){this.service=service;}
    @PostMapping public ResponseEntity<KycWebhookResult> receive(@RequestHeader("X-Kyc-Signature")String signature,@RequestBody String payload){return ResponseEntity.accepted().body(service.handle(payload,signature));}
}
