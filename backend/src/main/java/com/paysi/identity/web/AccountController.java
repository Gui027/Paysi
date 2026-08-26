package com.paysi.identity.web;

import com.paysi.identity.app.SignUpCommand;
import com.paysi.identity.app.SignUpService;
import com.paysi.identity.web.dto.AccountResponse;
import com.paysi.identity.web.dto.CreateAccountRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/accounts")
public class AccountController {

    private final SignUpService signUpService;

    public AccountController(SignUpService signUpService) {
        this.signUpService = signUpService;
    }

    @PostMapping
    public ResponseEntity<AccountResponse> create(@Valid @RequestBody CreateAccountRequest request) {
        var command = new SignUpCommand(request.fullName(), request.email(), request.password(),
                request.personType(), request.taxId(), request.initialMode(), request.termsHash());
        var created = signUpService.signUp(command);
        return ResponseEntity.status(HttpStatus.CREATED).body(AccountResponse.from(created));
    }
}
