package com.paysi.identity.kyc.web;

import com.paysi.identity.kyc.app.KycService;
import com.paysi.identity.kyc.app.KycService.PendingDocumentView;
import com.paysi.identity.kyc.app.KycView;
import com.paysi.identity.session.app.SessionService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/v1/accounts/me")
public class KycController {
    private static final String COOKIE_NAME = "paysi_session";
    private final KycService kyc;
    private final SessionService sessions;
    public KycController(KycService kyc, SessionService sessions) { this.kyc = kyc; this.sessions = sessions; }

    public record ComplianceProfileRequest(String postalCode, String birthDate, Long incomeValueCents) { }

    @GetMapping
    public KycView current(@CookieValue(name = COOKIE_NAME, required = false) String token) {
        return kyc.current(sessions.authenticate(token).session().accountId());
    }

    @PostMapping("/kyc")
    public KycView start(@CookieValue(name = COOKIE_NAME, required = false) String token) {
        return kyc.start(sessions.authenticate(token).session().accountId());
    }

    @PutMapping("/kyc/contact-info")
    public KycView saveComplianceProfile(@CookieValue(name = COOKIE_NAME, required = false) String token,
                                         @RequestBody ComplianceProfileRequest request) {
        return kyc.saveComplianceProfile(sessions.authenticate(token).session().accountId(), request.postalCode(), request.birthDate(), request.incomeValueCents());
    }

    @GetMapping("/kyc/documents")
    public List<PendingDocumentView> pendingDocuments(@CookieValue(name = COOKIE_NAME, required = false) String token) {
        return kyc.pendingDocuments(sessions.authenticate(token).session().accountId());
    }

    @PostMapping(value = "/kyc/documents/{documentGroupId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> submitDocument(@CookieValue(name = COOKIE_NAME, required = false) String token,
                                               @PathVariable String documentGroupId, @RequestPart MultipartFile file) throws IOException {
        kyc.submitDocument(sessions.authenticate(token).session().accountId(), documentGroupId, file.getBytes(),
                file.getOriginalFilename(), file.getContentType());
        return ResponseEntity.noContent().build();
    }
}
