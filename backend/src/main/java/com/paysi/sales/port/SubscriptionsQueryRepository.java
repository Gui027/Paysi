package com.paysi.sales.port;

import com.paysi.sales.app.SubscriptionsModels.SubscriptionDetail;
import com.paysi.sales.app.SubscriptionsModels.SubscriptionRow;
import com.paysi.sales.app.SubscriptionsModels.SubscriptionsFilter;
import com.paysi.sales.app.SubscriptionsModels.SubscriptionsSummary;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubscriptionsQueryRepository {
    List<SubscriptionRow> list(UUID sellerId, SubscriptionsFilter filter, int limit, int offset);

    /** Quantidade de assinaturas da aba selecionada. */
    long count(UUID sellerId, SubscriptionsFilter filter);

    /** Cartões de resumo: consideram os filtros, mas não a aba. */
    SubscriptionsSummary summarize(UUID sellerId, SubscriptionsFilter filter);

    Optional<SubscriptionDetail> find(UUID sellerId, UUID subscriptionId);
}
