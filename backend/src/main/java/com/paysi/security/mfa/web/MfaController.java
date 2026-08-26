package com.paysi.security.mfa.web;
import com.paysi.identity.session.app.SessionService;
import com.paysi.security.mfa.app.*;
import com.paysi.security.mfa.domain.SensitiveOperation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.UUID;
@RestController @RequestMapping("/v1/mfa")
public class MfaController {
    private static final String COOKIE_NAME="paysi_session"; private final MfaService mfa; private final SessionService sessions;
    public MfaController(MfaService mfa,SessionService sessions){this.mfa=mfa;this.sessions=sessions;}
    @PostMapping("/setup") public MfaEnrollment setup(@CookieValue(name=COOKIE_NAME,required=false)String token){var session=sessions.authenticate(token).session();return mfa.setup(session.accountId(),session.accountId().toString());}
    @PostMapping("/setup/confirm") public ResponseEntity<Void> confirm(@CookieValue(name=COOKIE_NAME,required=false)String token,@Valid @RequestBody CodeRequest request){mfa.confirmEnrollment(sessions.authenticate(token).session().accountId(),request.code());return ResponseEntity.noContent().build();}
    @PostMapping("/challenges") public ResponseEntity<MfaChallengeView> challenge(@CookieValue(name=COOKIE_NAME,required=false)String token,@Valid @RequestBody ChallengeRequest request){var created=mfa.challenge(sessions.authenticate(token).session().accountId(),request.operation());return ResponseEntity.created(URI.create("/v1/mfa/challenges/"+created.challengeId())).body(created);}
    @PostMapping("/challenges/{challengeId}/verify") public MfaChallengeView verify(@CookieValue(name=COOKIE_NAME,required=false)String token,@PathVariable UUID challengeId,@Valid @RequestBody CodeRequest request){return mfa.verify(sessions.authenticate(token).session().accountId(),challengeId,request.code());}
    public record CodeRequest(@NotBlank String code){}
    public record ChallengeRequest(@NotNull SensitiveOperation operation){}
}
