package com.paysi.reports.port;

import com.paysi.reports.app.ReportsModels.ReportFilter;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Leituras dos relatórios. Cada linha já vem na ordem das colunas do relatório; datas e instantes como texto ISO. */
public interface ReportsQueryRepository {
    /** Produto, vendas aprovadas, líquido. */
    List<List<Object>> revenueByProduct(UUID sellerId, LocalDate from, LocalDate to);

    /** Afiliado, vendas aprovadas, comissões. */
    List<List<Object>> revenueByAffiliate(UUID sellerId, LocalDate from, LocalDate to);

    /** Data, produto, cliente, e-mail, telefone, valor, status. */
    List<List<Object>> abandoned(UUID sellerId, ReportFilter filter);

    /** Data de liberação, total, para os meios de pagamento informados. */
    List<List<Object>> receivables(UUID sellerId, LocalDate from, LocalDate to, List<String> methods);

    /** Data do cancelamento, produto, plano, cliente, e-mail. */
    List<List<Object>> canceledSubscriptions(UUID sellerId, ReportFilter filter);
}
