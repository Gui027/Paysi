package com.paysi.reports.app;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Modelos dos relatórios do vendedor. Toda tabela carrega as próprias colunas (com o tipo de cada uma), então a
 * tela, o CSV e a planilha nascem da mesma estrutura. Valores em centavos e alturas do gráfico saem prontos daqui:
 * o navegador só formata texto.
 */
public final class ReportsModels {
    private ReportsModels() { }

    /** {@code type}: text, int, money, date (yyyy-MM-dd) ou datetime (instante ISO). */
    public record Column(String key, String label, String type) { }

    /** Alturas em porcentagem do eixo, com uma casa decimal ("42.5"). */
    public record ChartPoint(String label, long valueCents, String heightPercent, long secondCents, String secondPercent) { }

    public record AxisTick(long cents, String bottomPercent) { }

    public record Chart(List<ChartPoint> points, List<AxisTick> ticks) { }

    public record Report(String id, String title, List<Column> columns, List<List<Object>> rows, Chart chart,
                         Map<String, Long> totals, int page, int size, long total, int totalPages) { }

    public record ReportFilter(LocalDate from, LocalDate to, UUID productId, String query, String tab) { }

    /** Relatório inteiro, sem paginação: base da tela e da exportação. */
    public record ReportData(String id, String title, String fileName, List<Column> columns, List<List<Object>> rows,
                             Chart chart, Map<String, Long> totals) { }
}
