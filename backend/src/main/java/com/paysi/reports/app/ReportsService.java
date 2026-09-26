package com.paysi.reports.app;

import com.paysi.core.error.NotFoundException;
import com.paysi.core.error.ValidationException;
import com.paysi.reports.app.ReportsModels.Chart;
import com.paysi.reports.app.ReportsModels.Column;
import com.paysi.reports.app.ReportsModels.Report;
import com.paysi.reports.app.ReportsModels.ReportData;
import com.paysi.reports.app.ReportsModels.ReportFilter;
import com.paysi.reports.port.ReportsQueryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Relatórios do vendedor: cada um devolve colunas, linhas, gráfico e totais já calculados no banco. */
@Service
public class ReportsService {
    public static final int DEFAULT_PAGE_SIZE = 10;
    public static final int MAX_PAGE_SIZE = 50;
    static final Set<String> IDS = Set.of("co-producao-recebida", "co-producao-enviada", "produto", "afiliado",
            "abandonadas", "alunos", "saldo-receber", "recebiveis-cartao", "assinaturas-canceladas");

    private static final Column PRODUCT = new Column("product", "Produto", "text");
    private static final Column CLIENT = new Column("client", "Cliente", "text");
    private static final Column EMAIL = new Column("email", "E-mail", "text");

    private final ReportsQueryRepository repository;

    public ReportsService(ReportsQueryRepository repository) {
        this.repository = repository;
    }

    public ReportFilter filter(String from, String to, UUID productId, String query, String tab) {
        LocalDate start = date(from, "from");
        LocalDate end = date(to, "to");
        if (start != null && end != null && end.isBefore(start)) {
            throw new ValidationException("REPORT_PERIOD_INVALID", "A data final deve ser igual ou posterior à inicial", "to");
        }
        String text = query == null || query.isBlank() ? null : query.strip();
        if (text != null && text.length() > 120) {
            throw new ValidationException("REPORT_QUERY_TOO_LONG", "A busca deve ter no máximo 120 caracteres", "q");
        }
        return new ReportFilter(start, end, productId, text, tab == null || tab.isBlank() ? null : tab.strip());
    }

    @Transactional(readOnly = true)
    public Report report(UUID sellerId, String id, ReportFilter filter, Integer requestedPage, Integer requestedSize) {
        ReportData data = load(sellerId, id, filter);
        int size = requestedSize == null ? DEFAULT_PAGE_SIZE : Math.min(Math.max(requestedSize, 1), MAX_PAGE_SIZE);
        int total = data.rows().size();
        int totalPages = Math.max(1, (total + size - 1) / size);
        int page = requestedPage == null ? 1 : Math.min(Math.max(requestedPage, 1), totalPages);
        List<List<Object>> slice = data.rows().subList((page - 1) * size, Math.min(total, page * size));
        return new Report(data.id(), data.title(), data.columns(), slice, data.chart(), data.totals(), page, size, total, totalPages);
    }

    @Transactional(readOnly = true)
    public ReportData load(UUID sellerId, String id, ReportFilter filter) {
        return switch (id) {
            case "co-producao-recebida", "co-producao-enviada" -> coproduction(id);
            case "produto" -> ranking(id, "Receita por produto", "receita_por_produto", "Produto", "Total líquido",
                    repository.revenueByProduct(sellerId, filter.from(), filter.to()));
            case "afiliado" -> ranking(id, "Receita por afiliado", "receita_por_afiliado", "Afiliado", "Comissões",
                    repository.revenueByAffiliate(sellerId, filter.from(), filter.to()));
            case "abandonadas" -> abandoned(sellerId, filter);
            case "alunos" -> new ReportData(id, "Engajamento dos alunos", "engajamento_alunos",
                    List.of(new Column("student", "Aluno", "text"), PRODUCT, new Column("lastAccess", "Último acesso", "datetime")),
                    List.of(), ChartScale.empty(), Map.of());
            case "saldo-receber" -> receivables(sellerId, filter);
            case "recebiveis-cartao" -> cardReceivables(sellerId, filter);
            case "assinaturas-canceladas" -> canceled(sellerId, filter);
            default -> throw new NotFoundException("REPORT_NOT_FOUND", "Relatório não encontrado");
        };
    }

    /** A co-produção ainda não existe na plataforma: os dois relatórios abrem vazios até ela ser criada. */
    private static ReportData coproduction(String id) {
        boolean received = id.endsWith("recebida");
        return new ReportData(id, "Receita de co-produção", received ? "receita_coproducao_recebida" : "receita_coproducao_enviada",
                List.of(new Column("producer", "Produtor", "text"), new Column("document", "Documento", "text"),
                        new Column("total", "Total", "money")), List.of(), ChartScale.empty(), Map.of("total", 0L));
    }

    private static ReportData ranking(String id, String title, String file, String labelHeader, String valueHeader,
                                      List<List<Object>> rows) {
        List<String> labels = new ArrayList<>();
        List<Long> values = new ArrayList<>();
        long sum = 0;
        for (List<Object> row : rows) {
            labels.add((String) row.get(0));
            values.add((Long) row.get(2));
            sum += (Long) row.get(2);
        }
        return new ReportData(id, title, file,
                List.of(new Column("label", labelHeader, "text"), new Column("sales", "Vendas", "int"),
                        new Column("total", valueHeader, "money")),
                rows, ChartScale.build(labels, values, null), Map.of("total", sum));
    }

    private ReportData abandoned(UUID sellerId, ReportFilter filter) {
        List<List<Object>> rows = new ArrayList<>();
        for (List<Object> row : repository.abandoned(sellerId, filter)) {
            List<Object> copy = new ArrayList<>(row);
            copy.set(6, statusLabel((String) row.get(6)));
            rows.add(copy);
        }
        return new ReportData("abandonadas", "Vendas abandonadas", "vendas_abandonadas",
                List.of(new Column("date", "Data", "datetime"), PRODUCT, CLIENT, EMAIL,
                        new Column("phone", "Telefone", "text"), new Column("value", "Valor", "money"),
                        new Column("status", "Situação", "text")), rows, ChartScale.empty(), Map.of());
    }

    static String statusLabel(String status) {
        return switch (status) {
            case "PENDING" -> "Aguardando pagamento";
            case "EXPIRED" -> "Expirado";
            case "FAILED" -> "Recusado";
            default -> status;
        };
    }

    private ReportData receivables(UUID sellerId, ReportFilter filter) {
        String tab = filter.tab() == null ? "card" : filter.tab();
        List<String> methods = switch (tab) {
            case "card" -> List.of("CARD");
            case "offline" -> List.of("PIX", "BOLETO");
            case "international" -> List.of();
            default -> throw new ValidationException("REPORT_TAB_INVALID", "Aba inválida", "tab");
        };
        List<List<Object>> rows = repository.receivables(sellerId, filter.from(), filter.to(), methods);
        return new ReportData("saldo-receber", "Saldo a receber", "saldo_a_receber",
                List.of(new Column("date", "Data", "date"), new Column("total", "Total", "money")),
                rows, byDate(rows, 1, null), Map.of("total", sum(rows, 1)));
    }

    private ReportData cardReceivables(UUID sellerId, ReportFilter filter) {
        List<List<Object>> base = repository.receivables(sellerId, filter.from(), filter.to(), List.of("CARD"));
        List<List<Object>> rows = new ArrayList<>();
        for (List<Object> row : base) rows.add(List.of(row.get(0), "Cartão de crédito", row.get(1), 0L));
        List<Long> contract = new ArrayList<>();
        for (int index = 0; index < rows.size(); index++) contract.add(0L);
        Map<String, Long> totals = new LinkedHashMap<>();
        totals.put("receivable", sum(rows, 2));
        totals.put("contract", 0L);
        return new ReportData("recebiveis-cartao", "Recebíveis de cartão", "recebiveis_cartao",
                List.of(new Column("date", "Data", "date"), new Column("arrangement", "Arranjo", "text"),
                        new Column("receivable", "A receber", "money"), new Column("contract", "Efeito de contrato", "money")),
                rows, byDate(rows, 2, contract), totals);
    }

    private ReportData canceled(UUID sellerId, ReportFilter filter) {
        List<List<Object>> rows = new ArrayList<>();
        for (List<Object> row : repository.canceledSubscriptions(sellerId, filter)) {
            List<Object> copy = new ArrayList<>(row);
            copy.add("Não informado");
            rows.add(copy);
        }
        return new ReportData("assinaturas-canceladas", "Assinaturas canceladas", "assinaturas_canceladas",
                List.of(new Column("date", "Data", "datetime"), PRODUCT, new Column("plan", "Plano", "text"), CLIENT, EMAIL,
                        new Column("reason", "Motivo do cancelamento", "text")), rows, ChartScale.empty(), Map.of());
    }

    private static Chart byDate(List<List<Object>> rows, int valueIndex, List<Long> second) {
        List<String> labels = new ArrayList<>();
        List<Long> values = new ArrayList<>();
        for (List<Object> row : rows) {
            String day = (String) row.get(0);
            labels.add(day.substring(8, 10) + "/" + day.substring(5, 7));
            values.add((Long) row.get(valueIndex));
        }
        return ChartScale.build(labels, values, second);
    }

    private static long sum(List<List<Object>> rows, int index) {
        long total = 0;
        for (List<Object> row : rows) total += (Long) row.get(index);
        return total;
    }

    private static LocalDate date(String value, String field) {
        if (value == null || value.isBlank()) return null;
        try {
            return LocalDate.parse(value.strip());
        } catch (DateTimeParseException error) {
            throw new ValidationException("REPORT_DATE_INVALID", "Data inválida. Use o formato AAAA-MM-DD", field);
        }
    }
}
