package com.paysi.admin.dashboard.web;

import com.paysi.admin.dashboard.app.AdminDashboardAuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequestMapping("/v1/admin-dashboard/sessions")
public class AdminDashboardSessionController {
    public static final String COOKIE = "paysi_admin_session";
    private final AdminDashboardAuthService auth;
    private final boolean secureCookie;

    public AdminDashboardSessionController(AdminDashboardAuthService auth,
            @Value("${paysi.session.cookie-secure:false}") boolean secureCookie) {
        this.auth = auth;
        this.secureCookie = secureCookie;
    }

    @PostMapping
    public ResponseEntity<AdminView> login(@Valid @RequestBody LoginRequest request, HttpServletRequest servlet) {
        var authenticated = auth.login(request.username(), request.password(), servlet.getRemoteAddr());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header(HttpHeaders.SET_COOKIE, cookie(authenticated.rawToken()).toString())
                .body(new AdminView(authenticated.username()));
    }

    @GetMapping("/current")
    public ResponseEntity<AdminView> current(
            @CookieValue(name = COOKIE, required = false) String token) {
        var authenticated = auth.authenticate(token);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header(HttpHeaders.SET_COOKIE, cookie(authenticated.rawToken()).toString())
                .body(new AdminView(authenticated.username()));
    }

    @DeleteMapping("/current")
    public ResponseEntity<Void> logout(@CookieValue(name = COOKIE, required = false) String token) {
        auth.logout(token);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore())
                .header(HttpHeaders.SET_COOKIE, expiredCookie().toString()).build();
    }

    private ResponseCookie cookie(String value) {
        return ResponseCookie.from(COOKIE, value).httpOnly(true).secure(secureCookie).sameSite("Strict")
                .path("/").maxAge(AdminDashboardAuthService.SESSION_TTL).build();
    }

    private ResponseCookie expiredCookie() {
        return ResponseCookie.from(COOKIE, "").httpOnly(true).secure(secureCookie).sameSite("Strict")
                .path("/").maxAge(Duration.ZERO).build();
    }

    public record LoginRequest(@NotBlank @Size(max = 100) String username,
                               @NotBlank @Size(max = 200) String password) { }
    public record AdminView(String username) { }
}
