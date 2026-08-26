package com.paysi.identity.recovery.web;

import com.paysi.identity.recovery.app.PasswordRecoveryService;
import com.paysi.identity.recovery.web.dto.PasswordRecoveryRequest;
import com.paysi.identity.recovery.web.dto.PasswordResetRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/password-recovery")
public class PasswordRecoveryController {
    private final PasswordRecoveryService service;

    public PasswordRecoveryController(PasswordRecoveryService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<Void> request(@Valid @RequestBody PasswordRecoveryRequest request) {
        service.request(request.email());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/reset")
    public ResponseEntity<Void> reset(@Valid @RequestBody PasswordResetRequest request) {
        service.reset(request.token(), request.newPassword(), request.confirmPassword());
        return ResponseEntity.noContent().build();
    }
}
