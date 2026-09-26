package com.paysi.sales.app;

import com.paysi.sales.app.SubscriptionsModels.SubscriptionRow;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/** Exportação das assinaturas no mesmo formato das demais (Excel brasileiro, anti-fórmula). */
public final class SubscriptionsCsv {
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.ROOT)
            .withZone(ZoneId.of("America/Sao_Paulo"));
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ROOT)
            .withZone(ZoneId.of("America/Sao_Paulo"));
    private static final String HEADER = "ID da assinatura;Data de início;Produto;Plano;Cliente;E-mail;Status;Frequência;Valor líquido;Próxima cobrança";

    private SubscriptionsCsv() { }

    static String statusLabel(String status, boolean cancelPending) {
        if (cancelPending) return "Cancelamento agendado";
        return switch (status) {
            case "ACTIVE" -> "Ativo";
            case "TRIAL" -> "Em teste";
            case "PAST_DUE" -> "Em atraso";
            case "CANCELED" -> "Cancelado";
            default -> status;
        };
    }

    static String cycleLabel(String cycle) {
        if (cycle == null) return "";
        return switch (cycle) {
            case "MONTHLY" -> "Mensal";
            case "QUARTERLY" -> "Trimestral";
            case "SEMIANNUAL" -> "Semestral";
            case "ANNUAL" -> "Anual";
            default -> cycle;
        };
    }

    public static String build(List<SubscriptionRow> rows) {
        StringBuilder out = new StringBuilder("﻿").append(HEADER).append("\r\n");
        for (SubscriptionRow row : rows) {
            out.append(String.join(";",
                    SalesCsv.cell(row.code()),
                    SalesCsv.cell(DATE_TIME.format(row.createdAt())),
                    SalesCsv.cell(row.productName()),
                    SalesCsv.cell(row.offerName() == null ? "" : row.offerName()),
                    SalesCsv.cell(row.buyerName()),
                    SalesCsv.cell(row.buyerEmail()),
                    SalesCsv.cell(statusLabel(row.status(), row.cancelPending())),
                    SalesCsv.cell(cycleLabel(row.cycle())),
                    row.netCents() == null ? "" : SalesCsv.money(row.netCents()),
                    row.nextChargeAt() == null || "CANCELED".equals(row.status()) ? "" : DATE.format(row.nextChargeAt())))
                    .append("\r\n");
        }
        return out.toString();
    }
}
