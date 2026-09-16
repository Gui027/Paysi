package com.paysi.affiliate.web;

import com.paysi.affiliate.app.CommissionService;
import com.paysi.affiliate.web.dto.AffiliateClickRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/affiliate-clicks")
@Tag(name = "Afiliação")
public class AffiliateClickController {
    private final CommissionService commissions;

    public AffiliateClickController(CommissionService commissions) {
        this.commissions = commissions;
    }

    @PostMapping
    @Operation(summary = "Registrar clique em link de afiliado")
    public ResponseEntity<Void> register(@Valid @RequestBody AffiliateClickRequest request,
                                          HttpServletRequest httpRequest) {
        commissions.registerClick(request.productId(), request.affiliateId(), request.visitorKey(),
                httpRequest.getRemoteAddr());
        return ResponseEntity.accepted().build();
    }
}
