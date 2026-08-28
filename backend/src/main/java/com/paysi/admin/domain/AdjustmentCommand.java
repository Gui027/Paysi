package com.paysi.admin.domain;

import com.paysi.ledger.domain.Bucket;
import com.paysi.ledger.domain.Direction;

import java.util.UUID;

public record AdjustmentCommand(UUID accountId, Bucket bucket, Direction direction, long amountCents,
                                String reference, String reason) {
    public AdjustmentCommand {
        if (accountId == null || bucket == null || direction == null) {
            throw new IllegalArgumentException("Conta, bucket e direção são obrigatórios");
        }
        if (!bucket.userBucket()) throw new IllegalArgumentException("Ajuste exige bucket de usuário");
        if (amountCents <= 0) throw new IllegalArgumentException("Valor precisa ser positivo");
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("Motivo é obrigatório");
    }
}
