package com.paysi.finance.web;

import com.paysi.finance.app.FinanceModels.Fees;
import com.paysi.finance.app.FinanceModels.Overview;
import com.paysi.finance.app.FinanceModels.PayoutsPage;
import com.paysi.finance.app.FinanceModels.PixAccount;
import com.paysi.finance.app.FinanceService;
import com.paysi.identity.session.app.SessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Tela Financeiro: saldo, saques, chave Pix, taxas e prazos, e a mudança de CPF para CNPJ. */
@RestController
@RequestMapping("/v1/accounts/me")
@Tag(name = "Financeiro")
public class FinanceController {
    private static final String COOKIE_NAME = "paysi_session";

    private final FinanceService finance;
    private final SessionService sessions;

    public FinanceController(FinanceService finance, SessionService sessions) {
        this.finance = finance;
        this.sessions = sessions;
    }

    @GetMapping("/finance")
    @Operation(summary = "Visão geral do financeiro: titular, saldos, chave Pix e regras de saque")
    public Overview overview(@CookieValue(name = COOKIE_NAME, required = false) String token) {
        return finance.overview(accountId(token));
    }

    @GetMapping("/payouts")
    @Operation(summary = "Listar saques com paginação numerada")
    public PayoutsPage payouts(@CookieValue(name = COOKIE_NAME, required = false) String token,
                               @RequestParam(required = false) Integer page,
                               @RequestParam(required = false) Integer size) {
        return finance.payoutsPage(accountId(token), page, size);
    }

    @GetMapping("/fees")
    @Operation(summary = "Taxas do plano, prazo de recebimento e reserva de segurança")
    public Fees fees(@CookieValue(name = COOKIE_NAME, required = false) String token) {
        return finance.fees(accountId(token));
    }

    @PutMapping("/pix-key")
    @Operation(summary = "Cadastrar ou trocar a chave Pix de recebimento")
    public PixAccount savePixKey(@CookieValue(name = COOKIE_NAME, required = false) String token,
                                 @RequestHeader(name = "X-MFA-Challenge-Id", required = false) UUID challengeId,
                                 @Valid @RequestBody PixKeyRequest request) {
        return finance.savePixKey(accountId(token), request.pixKey(), challengeId);
    }

    @PostMapping("/convert-to-company")
    @Operation(summary = "Alterar a conta de CPF para CNPJ (irreversível)")
    public PixAccount convertToCompany(@CookieValue(name = COOKIE_NAME, required = false) String token,
                                       @RequestHeader(name = "X-MFA-Challenge-Id", required = false) UUID challengeId,
                                       @Valid @RequestBody CompanyRequest request) {
        return finance.convertToCompany(accountId(token), request.legalName(), request.cnpj(), request.pixKey(),
                challengeId);
    }

    private UUID accountId(String token) {
        return sessions.authenticate(token).session().accountId();
    }

    public record PixKeyRequest(@NotBlank @Size(max = 140) String pixKey) { }

    public record CompanyRequest(@NotBlank @Size(max = 120) String legalName, @NotBlank @Size(max = 32) String cnpj,
                                 @NotBlank @Size(max = 140) String pixKey) { }
}
