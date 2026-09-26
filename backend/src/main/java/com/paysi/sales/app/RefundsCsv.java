package com.paysi.sales.app;

import com.paysi.sales.app.SalesModels.RefundRow;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/** Exportação dos reembolsos no mesmo formato da de vendas (Excel brasileiro, anti-fórmula). */
public final class RefundsCsv {
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.ROOT)
            .withZone(ZoneId.of("America/Sao_Paulo"));
    private static final String HEADER = "Solicitação;ID da venda;Produto;Comprador;E-mail;Status;Autor;Valor líquido;Valor reembolsado;Motivo";

    private RefundsCsv() { }

    static String statusLabel(String status) {
        return switch (status) {
            case "SUCCEEDED" -> "Reembolsado";
            case "PENDING" -> "Em processamento";
            case "FAILED" -> "Falhou";
            default -> status;
        };
    }

    static String originLabel(String origin) {
        return switch (origin) {
            case "SELLER" -> "Vendedor";
            case "BUYER" -> "Comprador";
            default -> "Paysi";
        };
    }

    public static String build(List<RefundRow> rows) {
        StringBuilder out = new StringBuilder("\uFEFF").append(HEADER).append("\r\n");
        for (RefundRow row : rows) {
            out.append(String.join(";",
                    SalesCsv.cell(DATE_TIME.format(row.createdAt())),
                    SalesCsv.cell(row.saleCode()),
                    SalesCsv.cell(row.productName()),
                    SalesCsv.cell(row.buyerName()),
                    SalesCsv.cell(row.buyerEmail()),
                    SalesCsv.cell(statusLabel(row.status())),
                    SalesCsv.cell(originLabel(row.requestedBy())),
                    SalesCsv.money(row.sellerCents()),
                    SalesCsv.money(row.amountCents()),
                    SalesCsv.cell(row.reason()))).append("\r\n");
        }
        return out.toString();
    }
}
