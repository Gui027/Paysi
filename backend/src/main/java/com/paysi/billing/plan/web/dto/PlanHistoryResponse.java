package com.paysi.billing.plan.web.dto;

import com.paysi.billing.plan.port.PlanRepository.PlanChangeRecord;

import java.time.Instant;
import java.util.UUID;

public record PlanHistoryResponse(UUID id, String fromPlan, String toPlan, String priceTable, Instant createdAt) {
    public static PlanHistoryResponse from(PlanChangeRecord record) {
        return new PlanHistoryResponse(record.id(), record.fromPlan(), record.toPlan(), record.priceTable(),
                record.createdAt());
    }
}
