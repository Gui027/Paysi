package com.paysi.identity.session.web;

import com.paysi.identity.session.app.AuthenticatedSession;
import com.paysi.identity.session.app.SessionService;
import com.paysi.identity.session.app.SessionView;
import com.paysi.identity.session.web.dto.LoginRequest;
import com.paysi.identity.session.web.dto.SwitchModeRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequestMapping("/v1/sessions")
public class SessionController {
    static final String COOKIE_NAME = "paysi_session";

    private final SessionService service;
    private final boolean secureCookie;

    public SessionController(SessionService service,
                             @Value("${paysi.session.cookie-secure:false}") boolean secureCookie) {
        this.service = service;
        this.secureCookie = secureCookie;
    }

    @PostMapping
    public ResponseEntity<SessionView> login(@Valid @RequestBody LoginRequest request) {
        return withCookie(service.login(request.email(), request.password(), request.resolvedMode()));
    }

    @GetMapping("/current")
    public ResponseEntity<SessionView> current(
            @CookieValue(name = COOKIE_NAME, required = false) String token) {
        return withCookie(service.authenticate(token));
    }

    @PatchMapping("/current/mode")
    public ResponseEntity<SessionView> switchMode(
            @CookieValue(name = COOKIE_NAME, required = false) String token,
            @Valid @RequestBody SwitchModeRequest request) {
        return withCookie(service.switchMode(token, request.mode()));
    }

    @DeleteMapping("/current")
    public ResponseEntity<Void> logout(@CookieValue(name = COOKIE_NAME, required = false) String token) {
        service.logout(token);
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, expiredCookie().toString()).build();
    }

    private ResponseEntity<SessionView> withCookie(AuthenticatedSession authenticated) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, sessionCookie(authenticated.rawToken()).toString())
                .body(authenticated.session());
    }

    private ResponseCookie sessionCookie(String token) {
        return ResponseCookie.from(COOKIE_NAME, token).httpOnly(true).secure(secureCookie).sameSite("Lax")
                .path("/").maxAge(Duration.ofHours(12)).build();
    }

    private ResponseCookie expiredCookie() {
        return ResponseCookie.from(COOKIE_NAME, "").httpOnly(true).secure(secureCookie).sameSite("Lax")
                .path("/").maxAge(Duration.ZERO).build();
    }
}
