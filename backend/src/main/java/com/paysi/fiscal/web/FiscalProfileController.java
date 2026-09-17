package com.paysi.fiscal.web;

import com.paysi.fiscal.app.FiscalProfileCommand;
import com.paysi.fiscal.app.FiscalProfileService;
import com.paysi.fiscal.domain.FiscalProfile;
import com.paysi.identity.session.app.SessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** BE-14.1: perfil fiscal do vendedor autenticado (RF-095), sempre escopado à conta da sessão. */
@RestController
@Tag(name = "Fiscal")
public class FiscalProfileController {
    private static final String SESSION_COOKIE = "paysi_session";

    private final FiscalProfileService profiles;
    private final SessionService sessions;

    public FiscalProfileController(FiscalProfileService profiles, SessionService sessions) {
        this.profiles = profiles;
        this.sessions = sessions;
    }

    @PutMapping("/v1/fiscal/profile")
    @Operation(summary = "Criar ou atualizar o perfil fiscal e validar em homologação")
    public ResponseEntity<FiscalProfileResponse> save(
            @CookieValue(name = SESSION_COOKIE, required = false) String token,
            @RequestBody FiscalProfileRequest request) {
        var accountId = sessions.authenticate(token).session().accountId();
        var saved = profiles.saveAndValidate(accountId, request.toCommand());
        return ResponseEntity.ok(FiscalProfileResponse.from(saved));
    }

    @GetMapping("/v1/fiscal/profile")
    @Operation(summary = "Consultar o perfil fiscal da conta autenticada")
    public ResponseEntity<FiscalProfileResponse> get(
            @CookieValue(name = SESSION_COOKIE, required = false) String token) {
        var accountId = sessions.authenticate(token).session().accountId();
        return ResponseEntity.ok(FiscalProfileResponse.from(profiles.find(accountId)));
    }

    public record FiscalProfileRequest(String municipalityCode, String municipalRegistration, String serviceItem,
                                        Integer taxBps, String taxRegime, String credentialRef) {
        FiscalProfileCommand toCommand() {
            return new FiscalProfileCommand(municipalityCode, municipalRegistration, serviceItem, taxBps,
                    taxRegime, credentialRef);
        }
    }

    public record FiscalProfileResponse(String municipalityCode, String municipalRegistration, String serviceItem,
                                         int taxBps, String taxRegime, boolean validated) {
        static FiscalProfileResponse from(FiscalProfile profile) {
            return new FiscalProfileResponse(profile.municipalityCode(), profile.municipalRegistration(),
                    profile.serviceItem(), profile.taxBps(), profile.taxRegime(), profile.validated());
        }
    }
}
