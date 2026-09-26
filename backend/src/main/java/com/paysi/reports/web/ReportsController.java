package com.paysi.reports.web;

import com.paysi.core.error.ValidationException;
import com.paysi.identity.session.app.SessionService;
import com.paysi.reports.app.ReportExports;
import com.paysi.reports.app.ReportsModels.Report;
import com.paysi.reports.app.ReportsModels.ReportData;
import com.paysi.reports.app.ReportsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Relatórios do vendedor logado, com exportação em CSV e planilha (XLSX). */
@RestController
@Tag(name = "Relatórios")
public class ReportsController {
    private static final String COOKIE_NAME = "paysi_session";
    private static final MediaType XLSX = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final ReportsService reports;
    private final SessionService sessions;

    public ReportsController(ReportsService reports, SessionService sessions) {
        this.reports = reports;
        this.sessions = sessions;
    }

    @GetMapping("/v1/reports/{id}")
    @Operation(summary = "Consultar um relatório: colunas, linhas, gráfico e totais")
    public Report report(@CookieValue(name = COOKIE_NAME, required = false) String token,
                         @PathVariable String id,
                         @RequestParam(required = false) String from,
                         @RequestParam(required = false) String to,
                         @RequestParam(required = false) UUID productId,
                         @RequestParam(name = "q", required = false) String query,
                         @RequestParam(required = false) String tab,
                         @RequestParam(required = false) Integer page,
                         @RequestParam(required = false) Integer size) {
        return reports.report(accountId(token), id, reports.filter(from, to, productId, query, tab), page, size);
    }

    @GetMapping("/v1/reports/{id}/export")
    @Operation(summary = "Exportar um relatório em CSV ou XLSX")
    public ResponseEntity<byte[]> export(@CookieValue(name = COOKIE_NAME, required = false) String token,
                                         @PathVariable String id,
                                         @RequestParam(defaultValue = "csv") String format,
                                         @RequestParam(required = false) String from,
                                         @RequestParam(required = false) String to,
                                         @RequestParam(required = false) UUID productId,
                                         @RequestParam(name = "q", required = false) String query,
                                         @RequestParam(required = false) String tab) {
        boolean xlsx = "xlsx".equalsIgnoreCase(format);
        if (!xlsx && !"csv".equalsIgnoreCase(format)) {
            throw new ValidationException("REPORT_FORMAT_INVALID", "Formato inválido. Use csv ou xlsx", "format");
        }
        ReportData data = reports.load(accountId(token), id, reports.filter(from, to, productId, query, tab));
        byte[] body = xlsx ? ReportExports.xlsx(data) : ReportExports.csv(data);
        return ResponseEntity.ok()
                .contentType(xlsx ? XLSX : new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("paysi_" + data.fileName() + (xlsx ? ".xlsx" : ".csv")).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(body);
    }

    private UUID accountId(String token) {
        return sessions.authenticate(token).session().accountId();
    }
}
