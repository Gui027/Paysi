package com.paysi.billing.plan.web;

import com.paysi.billing.plan.app.PlanService;
import com.paysi.billing.plan.web.dto.PlanChangeRequest;
import com.paysi.billing.plan.web.dto.PlanHistoryResponse;
import com.paysi.billing.plan.web.dto.PlanResponse;
import com.paysi.core.error.ValidationException;
import com.paysi.identity.session.app.SessionService;
import com.paysi.payment.split.Plan;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/accounts/me/plan")
@Tag(name = "Plano comercial")
public class PlanController {
    private static final String COOKIE_NAME = "paysi_session";

    private final PlanService plans;
    private final SessionService sessions;

    public PlanController(PlanService plans, SessionService sessions) {
        this.plans = plans;
        this.sessions = sessions;
    }

    @GetMapping
    @Operation(summary = "Consultar plano comercial vigente")
    public PlanResponse get(@CookieValue(name = COOKIE_NAME, required = false) String token) {
        return PlanResponse.from(plans.get(accountId(token)));
    }

    @GetMapping("/history")
    @Operation(summary = "Histórico de mudanças de plano")
    public List<PlanHistoryResponse> history(
            @CookieValue(name = COOKIE_NAME, required = false) String token,
            @RequestParam(required = false, defaultValue = "20") int limit) {
        return plans.history(accountId(token), limit).stream().map(PlanHistoryResponse::from).toList();
    }

    @PostMapping("/change")
    @Operation(summary = "Solicitar troca de plano (vale a partir do próximo ciclo)")
    public PlanResponse requestChange(
            @CookieValue(name = COOKIE_NAME, required = false) String token,
            @Valid @RequestBody PlanChangeRequest request) {
        Plan newPlan = parsePlan(request.plan());
        return PlanResponse.from(plans.requestChange(accountId(token), newPlan, request.cardToken()));
    }

    private static Plan parsePlan(String value) {
        try {
            return Plan.valueOf(value);
        } catch (IllegalArgumentException error) {
            throw new ValidationException("PLAN_INVALID", "Plano inválido", "plan");
        }
    }

    private java.util.UUID accountId(String token) {
        return sessions.authenticate(token).session().accountId();
    }
}
