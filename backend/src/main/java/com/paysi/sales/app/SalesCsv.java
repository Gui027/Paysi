package com.paysi.sales.app;

import com.paysi.sales.app.SalesModels.SaleRow;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Exportação das vendas em CSV para o Excel brasileiro: separador ponto e vírgula, vírgula decimal e
 * BOM UTF-8. Todo texto vindo do comprador é neutralizado contra injeção de fórmula em planilha.
 */
public final class SalesCsv {
    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.ROOT).withZone(ZONE);
    private static final String HEADER = "ID da venda;Data;Aprovação;Produto;Oferta;Cliente;E-mail;Status;Método;Parcelas;Valor líquido";

    private SalesCsv() { }

    static String statusLabel(String status) {
        return switch (status) {
            case "PAID" -> "Pago";
            case "PENDING" -> "Aguardando pagamento";
            case "FAILED" -> "Recusado";
            case "EXPIRED" -> "Expirado";
            case "REFUNDED" -> "Reembolsado";
            case "PARTIALLY_REFUNDED" -> "Reembolso parcial";
            case "CHARGEBACK" -> "Chargeback";
            default -> status;
        };
    }

    static String methodLabel(String method) {
        return switch (method) {
            case "PIX" -> "Pix";
            case "CARD" -> "Cartão de crédito";
            case "BOLETO" -> "Boleto";
            default -> method;
        };
    }

    public static String build(List<SaleRow> rows) {
        StringBuilder out = new StringBuilder("﻿").append(HEADER).append("\r\n");
        for (SaleRow row : rows) {
            out.append(String.join(";",
                    cell(row.code()),
                    cell(DATE_TIME.format(row.createdAt())),
                    cell(row.approvedAt() == null ? "" : DATE_TIME.format(row.approvedAt())),
                    cell(row.productName()),
                    cell(row.offerName() == null ? "" : row.offerName()),
                    cell(row.buyerName()),
                    cell(row.buyerEmail()),
                    cell(statusLabel(row.status())),
                    cell(methodLabel(row.method())),
                    Integer.toString(row.installments()),
                    money(row.netCents()))).append("\r\n");
        }
        return out.toString();
    }

    static String money(long cents) {
        return BigDecimal.valueOf(cents, 2).toPlainString().replace('.', ',');
    }

    /** Aspas duplicadas, e um apóstrofo na frente quando o texto começa como fórmula (= + - @ tab). */
    static String cell(String value) {
        String text = value == null ? "" : value;
        if (!text.isEmpty() && "=+-@\t\r".indexOf(text.charAt(0)) >= 0) text = "'" + text;
        if (text.contains(";") || text.contains("\"") || text.contains("\n") || text.contains("\r")) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }
}
