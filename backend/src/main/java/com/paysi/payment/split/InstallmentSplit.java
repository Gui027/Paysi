package com.paysi.payment.split;

import java.util.ArrayList;
import java.util.List;

public final class InstallmentSplit {
    private InstallmentSplit() {}

    /** Maior resto: soma exata e desvio máximo de um centavo. */
    public static List<Long> byInstallment(long totalCents, int installments) {
        if (totalCents < 0) throw new IllegalArgumentException("total não pode ser negativo");
        if (installments < 1 || installments > 12) {
            throw new IllegalArgumentException("parcelas devem estar entre 1 e 12");
        }
        long base = totalCents / installments;
        long remainder = totalCents % installments;
        List<Long> result = new ArrayList<>(installments);
        for (int index = 0; index < installments; index++) {
            result.add(base + (index < remainder ? 1 : 0));
        }
        return List.copyOf(result);
    }
}

