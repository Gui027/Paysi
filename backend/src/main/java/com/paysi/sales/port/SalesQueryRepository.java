package com.paysi.sales.port;

import com.paysi.sales.app.SalesModels.RefundFilter;
import com.paysi.sales.app.SalesModels.RefundRow;
import com.paysi.sales.app.SalesModels.SaleDetail;
import com.paysi.sales.app.SalesModels.SaleRow;
import com.paysi.sales.app.SalesModels.SalesFilter;
import com.paysi.sales.app.SalesModels.SalesSummary;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SalesQueryRepository {
    List<SaleRow> list(UUID sellerId, SalesFilter filter, int limit, int offset);

    /** Quantidade de vendas do filtro e valor líquido das aprovadas dentro dele. */
    SalesSummary summarize(UUID sellerId, SalesFilter filter);

    Optional<SaleDetail> find(UUID sellerId, UUID chargeId);

    List<RefundRow> listRefunds(UUID sellerId, RefundFilter filter, int limit, int offset);

    long countRefunds(UUID sellerId, RefundFilter filter);
}
