package com.paysi.billing.plan.app;

import com.paysi.billing.plan.domain.PlatformSubscription;
import com.paysi.billing.plan.domain.PriceTable;
import com.paysi.billing.plan.port.PlanRepository;
import com.paysi.core.error.NotFoundException;
import com.paysi.core.error.ValidationException;
import com.paysi.payment.split.Plan;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/** BE-14.2: leitura do plano vigente e agendamento de troca (RF-101/114, sem efeito retroativo). */
@Service
public class PlanService {
    private final PlanRepository plans;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public PlanService(PlanRepository plans) {
        this(plans, Clock.systemUTC());
    }

    PlanService(PlanRepository plans, Clock clock) {
        this.plans = plans;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PlatformSubscription get(UUID accountId) {
        return plans.find(accountId).orElseThrow(PlanService::notFound);
    }

    @Transactional(readOnly = true)
    public List<PlanRepository.PlanChangeRecord> history(UUID accountId, int limit) {
        return plans.listHistory(accountId, Math.max(1, Math.min(limit, 100)));
    }

    /**
     * Agenda a troca para o fim do ciclo vigente (RF-114: nunca retroativa; cobranças já criadas
     * não mudam). Escala exige cartão cadastrado como alternativa caso o saldo não cubra (RF-102).
     */
    @Transactional
    public PlatformSubscription requestChange(UUID accountId, Plan newPlan, String cardToken) {
        var current = plans.find(accountId).orElseThrow(PlanService::notFound);
        if (newPlan == Plan.ESCALA && (cardToken == null || cardToken.isBlank()) && current.providerToken() == null) {
            throw new ValidationException("CARD_REQUIRED_FOR_ESCALA",
                    "Escala exige um cartão cadastrado como alternativa ao saldo", "cardToken");
        }
        long price = PriceTable.priceFor(newPlan);
        plans.schedulePendingChange(accountId, newPlan, price, current.currentPeriodEnd(), cardToken);
        plans.insertHistory(UUID.randomUUID(), accountId, current.plan().name(), newPlan.name(), accountId,
                PriceTable.VERSION, clock.instant());
        return plans.find(accountId).orElseThrow(PlanService::notFound);
    }

    private static NotFoundException notFound() {
        return new NotFoundException("PLAN_NOT_FOUND", "Plano comercial não encontrado");
    }
}
