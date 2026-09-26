package com.paysi.reports.app;

import com.paysi.reports.app.ReportsModels.Column;
import com.paysi.reports.app.ReportsModels.ReportData;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Exportação dos relatórios em CSV (Excel brasileiro: ponto e vírgula, vírgula decimal, BOM UTF-8) e em planilha
 * XLSX (valores numéricos de verdade). Textos do comprador são neutralizados contra injeção de fórmula no CSV.
 */
public final class ReportExports {
    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ROOT);
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.ROOT).withZone(ZONE);

    private ReportExports() { }

    /** Texto de uma célula como aparece na planilha (dinheiro com vírgula decimal). */
    static String text(Column column, Object value) {
        if (value == null) return "";
        return switch (column.type()) {
            case "money" -> BigDecimal.valueOf((Long) value, 2).toPlainString().replace('.', ',');
            case "date" -> value.toString().isEmpty() ? "" : DATE.format(LocalDate.parse(value.toString()));
            case "datetime" -> value.toString().isEmpty() ? "" : DATE_TIME.format(Instant.parse(value.toString()));
            default -> value.toString();
        };
    }

    public static byte[] csv(ReportData data) {
        StringBuilder out = new StringBuilder("﻿");
        out.append(String.join(";", data.columns().stream().map(column -> cell(column.label())).toList())).append("\r\n");
        for (List<Object> row : data.rows()) {
            for (int index = 0; index < data.columns().size(); index++) {
                if (index > 0) out.append(';');
                out.append(cell(text(data.columns().get(index), row.get(index))));
            }
            out.append("\r\n");
        }
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }

    static String cell(String value) {
        String text = value == null ? "" : value;
        if (!text.isEmpty() && "=+-@\t\r".indexOf(text.charAt(0)) >= 0) text = "'" + text;
        if (text.contains(";") || text.contains("\"") || text.contains("\n") || text.contains("\r")) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }

    public static byte[] xlsx(ReportData data) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
            put(zip, "[Content_Types].xml", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">\
                    <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>\
                    <Default Extension="xml" ContentType="application/xml"/>\
                    <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>\
                    <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>\
                    <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>\
                    </Types>""");
            put(zip, "_rels/.rels", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">\
                    <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>\
                    </Relationships>""");
            put(zip, "xl/workbook.xml", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">\
                    <sheets><sheet name="Relatório" sheetId="1" r:id="rId1"/></sheets></workbook>""");
            put(zip, "xl/_rels/workbook.xml.rels", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">\
                    <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>\
                    <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>\
                    </Relationships>""");
            put(zip, "xl/styles.xml", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">\
                    <fonts count="1"><font><sz val="11"/><name val="Calibri"/></font></fonts>\
                    <fills count="2"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill></fills>\
                    <borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>\
                    <cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>\
                    <cellXfs count="2"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>\
                    <xf numFmtId="2" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/></cellXfs>\
                    </styleSheet>""");
            put(zip, "xl/worksheets/sheet1.xml", sheet(data));
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
        return bytes.toByteArray();
    }

    private static String sheet(ReportData data) {
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>");
        xml.append("<row r=\"1\">");
        for (int index = 0; index < data.columns().size(); index++) {
            xml.append(inline(reference(index, 1), data.columns().get(index).label()));
        }
        xml.append("</row>");
        int number = 2;
        for (List<Object> row : data.rows()) {
            xml.append("<row r=\"").append(number).append("\">");
            for (int index = 0; index < data.columns().size(); index++) {
                Column column = data.columns().get(index);
                String ref = reference(index, number);
                Object value = row.get(index);
                if ("money".equals(column.type())) {
                    xml.append("<c r=\"").append(ref).append("\" s=\"1\"><v>")
                            .append(BigDecimal.valueOf((Long) value, 2).toPlainString()).append("</v></c>");
                } else if ("int".equals(column.type())) {
                    xml.append("<c r=\"").append(ref).append("\"><v>").append(value).append("</v></c>");
                } else {
                    xml.append(inline(ref, text(column, value)));
                }
            }
            xml.append("</row>");
            number++;
        }
        return xml.append("</sheetData></worksheet>").toString();
    }

    private static String inline(String ref, String value) {
        return "<c r=\"" + ref + "\" t=\"inlineStr\"><is><t xml:space=\"preserve\">" + escape(value) + "</t></is></c>";
    }

    /** Coluna A, B, … Z, AA (os relatórios têm poucas colunas, mas o cálculo cobre qualquer índice). */
    static String reference(int column, int row) {
        StringBuilder name = new StringBuilder();
        for (int value = column; value >= 0; value = value / 26 - 1) name.insert(0, (char) ('A' + value % 26));
        return name.toString() + row;
    }

    private static String escape(String value) {
        StringBuilder out = new StringBuilder(value.length());
        for (char letter : value.toCharArray()) {
            switch (letter) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                default -> {
                    if (letter >= 0x20 || letter == '\n' || letter == '\t') out.append(letter);
                }
            }
        }
        return out.toString();
    }

    private static void put(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
