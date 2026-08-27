package com.paysi.payment.receivable.domain;

import com.paysi.payment.split.InstallmentSplit;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

public final class ReceivableSchedule {
    private ReceivableSchedule() { }

    public static List<Receivable> create(ChargeReceivableTerms charge, List<ProviderInstallment> providerSchedule) {
        if (providerSchedule == null || providerSchedule.size() != charge.installments()) {
            throw new IllegalArgumentException("Quantidade de parcelas divergente da cobrança");
        }
        var ordered = providerSchedule.stream().sorted(Comparator.comparingInt(ProviderInstallment::sequence)).toList();
        var providerIds = new HashSet<String>();
        long total = 0;
        for (int index = 0; index < ordered.size(); index++) {
            var installment = ordered.get(index);
            if (installment.sequence() != index + 1 || !providerIds.add(installment.providerId())) {
                throw new IllegalArgumentException("Sequência ou ID de recebível duplicado");
            }
            total = Math.addExact(total, installment.amountCents());
        }
        if (total != charge.amountCents()) throw new IllegalArgumentException("Total das parcelas divergente da cobrança");

        var seller = InstallmentSplit.byInstallment(charge.sellerAmountCents(), charge.installments());
        var affiliate = InstallmentSplit.byInstallment(charge.affiliateAmountCents(), charge.installments());
        var result = new ArrayList<Receivable>(charge.installments());
        for (int index = 0; index < ordered.size(); index++) {
            var installment = ordered.get(index);
            if (seller.get(index) + affiliate.get(index) > installment.amountCents()) {
                throw new IllegalArgumentException("Partes da parcela excedem o valor informado pelo provedor");
            }
            result.add(new Receivable(UUID.randomUUID(), charge.chargeId(), installment.sequence(),
                    installment.providerId(), installment.expectedAt(), installment.amountCents(),
                    seller.get(index), affiliate.get(index)));
        }
        return List.copyOf(result);
    }

    public record ChargeReceivableTerms(UUID chargeId, int installments, long amountCents,
                                        long sellerAmountCents, long affiliateAmountCents) { }
}
