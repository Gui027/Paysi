package com.paysi.reports.app;

import com.paysi.core.error.NotFoundException;
import com.paysi.core.error.ValidationException;
import com.paysi.reports.app.ReportsModels.Column;
import com.paysi.reports.app.ReportsModels.Report;
import com.paysi.reports.app.ReportsModels.ReportData;
import com.paysi.reports.port.ReportsQueryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReportsServiceTest {
    private static final UUID SELLER = UUID.randomUUID();

    private ReportsQueryRepository repository;
    private ReportsService service;

    @BeforeEach
    void setUp() {
        repository = mock(ReportsQueryRepository.class);
        service = new ReportsService(repository);
    }

    @Test
    void chartAxisIsRoundAndBarsAreProportional() {
        var chart = ChartScale.build(List.of("A", "B"), List.of(2146L, 89L), List.of(0L, 0L));
        assertThat(chart.ticks()).extracting(tick -> tick.cents()).containsExactly(0L, 500L, 1000L, 1500L, 2000L, 2500L);
        assertThat(chart.ticks().get(5).bottomPercent()).isEqualTo("100.0");
        assertThat(chart.points().get(0).heightPercent()).isEqualTo("85.8");
        assertThat(chart.points().get(1).heightPercent()).isEqualTo("3.6");
        assertThat(ChartScale.empty().points()).isEmpty();
        assertThat(ChartScale.niceStep(1)).isEqualTo(1);
        assertThat(ChartScale.niceStep(430)).isEqualTo(500);
    }

    @Test
    void rankingByProductSumsTotalsAndPagesInTheService() {
        List<List<Object>> rows = new ArrayList<>();
        for (int index = 0; index < 12; index++) rows.add(List.of("Produto " + index, 1L, 1000L));
        when(repository.revenueByProduct(eq(SELLER), any(), any())).thenReturn(rows);

        Report first = service.report(SELLER, "produto", service.filter(null, null, null, null, null), null, null);
        assertThat(first.total()).isEqualTo(12);
        assertThat(first.totalPages()).isEqualTo(2);
        assertThat(first.rows()).hasSize(10);
        assertThat(first.totals()).containsEntry("total", 12000L);
        assertThat(first.chart().points()).hasSize(12);

        Report beyond = service.report(SELLER, "produto", service.filter(null, null, null, null, null), 9, 10);
        assertThat(beyond.page()).isEqualTo(2);
        assertThat(beyond.rows()).hasSize(2);
    }

    @Test
    void receivablesTabsMapToPaymentMethodsAndInternationalIsEmpty() {
        when(repository.receivables(eq(SELLER), any(), any(), eq(List.of("CARD")))).thenReturn(List.<List<Object>>of(List.of("2026-10-09", 2146L)));
        when(repository.receivables(eq(SELLER), any(), any(), eq(List.of("PIX", "BOLETO")))).thenReturn(List.of());
        when(repository.receivables(eq(SELLER), any(), any(), eq(List.of()))).thenReturn(List.of());

        Report card = service.report(SELLER, "saldo-receber", service.filter(null, null, null, null, "card"), 1, 10);
        assertThat(card.totals()).containsEntry("total", 2146L);
        assertThat(card.chart().points().get(0).label()).isEqualTo("09/10");
        assertThat(service.report(SELLER, "saldo-receber", service.filter(null, null, null, null, "offline"), 1, 10).rows()).isEmpty();
        assertThat(service.report(SELLER, "saldo-receber", service.filter(null, null, null, null, "international"), 1, 10).rows()).isEmpty();
        assertThatThrownBy(() -> service.report(SELLER, "saldo-receber", service.filter(null, null, null, null, "x"), 1, 10))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void cardReceivablesHaveContractEffectColumnAndCardArrangement() {
        when(repository.receivables(eq(SELLER), any(), any(), eq(List.of("CARD")))).thenReturn(List.<List<Object>>of(List.of("2026-10-09", 2146L)));
        ReportData data = service.load(SELLER, "recebiveis-cartao", service.filter(null, null, null, null, null));
        assertThat(data.rows().get(0)).containsExactly("2026-10-09", "Cartão de crédito", 2146L, 0L);
        assertThat(data.totals()).containsEntry("receivable", 2146L).containsEntry("contract", 0L);
    }

    @Test
    void coproductionAndStudentsOpenEmptyAndUnknownReportIs404() {
        assertThat(service.load(SELLER, "co-producao-recebida", service.filter(null, null, null, null, null)).rows()).isEmpty();
        assertThat(service.load(SELLER, "alunos", service.filter(null, null, null, null, null)).rows()).isEmpty();
        verify(repository, never()).revenueByProduct(any(), any(), any());
        assertThatThrownBy(() -> service.load(SELLER, "inexistente", service.filter(null, null, null, null, null)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void filterValidatesDatesAndSearchLength() {
        assertThat(service.filter("2026-09-01", "2026-09-30", null, "  ana ", null).query()).isEqualTo("ana");
        assertThat(service.filter("2026-09-01", null, null, null, null).from()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThatThrownBy(() -> service.filter("2026-09-30", "2026-09-01", null, null, null)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> service.filter("01/09/2026", null, null, null, null)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> service.filter(null, null, null, "x".repeat(121), null)).isInstanceOf(ValidationException.class);
    }

    @Test
    void csvUsesBrazilianExcelFormatAndNeutralizesFormulas() {
        List<Column> columns = List.of(new Column("d", "Data de liquidação", "date"), new Column("a", "Arranjo", "text"),
                new Column("v", "Valor a receber", "money"));
        ReportData data = new ReportData("x", "X", "x", columns,
                List.of(List.of("2026-10-09", "=cmd|calc", 2146L)), ChartScale.empty(), java.util.Map.of());
        String csv = new String(ReportExports.csv(data), StandardCharsets.UTF_8);
        assertThat(csv).startsWith("﻿Data de liquidação;Arranjo;Valor a receber\r\n");
        assertThat(csv).contains("09/10/2026;'=cmd|calc;21,46\r\n");
    }

    @Test
    void xlsxIsAZipWithNumericMoneyAndEscapedText() throws Exception {
        List<Column> columns = List.of(new Column("a", "Arranjo & cia", "text"), new Column("v", "Valor", "money"), new Column("n", "Qtd", "int"));
        ReportData data = new ReportData("x", "X", "x", columns, List.of(List.of("<Master>", 2146L, 3L)), ChartScale.empty(), java.util.Map.of());
        byte[] bytes = ReportExports.xlsx(data);
        StringBuilder sheet = new StringBuilder();
        List<String> names = new ArrayList<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                names.add(entry.getName());
                if (entry.getName().equals("xl/worksheets/sheet1.xml")) sheet.append(new String(zip.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        assertThat(names).contains("[Content_Types].xml", "xl/workbook.xml", "xl/styles.xml", "xl/worksheets/sheet1.xml");
        assertThat(sheet.toString()).contains("Arranjo &amp; cia").contains("&lt;Master&gt;")
                .contains("<c r=\"B2\" s=\"1\"><v>21.46</v></c>").contains("<c r=\"C2\"><v>3</v></c>");
        assertThat(ReportExports.reference(0, 1)).isEqualTo("A1");
        assertThat(ReportExports.reference(26, 5)).isEqualTo("AA5");
    }
}
