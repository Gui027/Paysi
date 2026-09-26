package com.paysi.webhook.port;

import com.paysi.webhook.app.WebhookPanelModels.EndpointItem;
import com.paysi.webhook.app.WebhookPanelModels.LogDetail;
import com.paysi.webhook.app.WebhookPanelModels.LogFilter;
import com.paysi.webhook.app.WebhookPanelModels.LogRow;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WebhookQueryRepository {
    List<EndpointItem> endpoints(UUID accountId, String query, UUID productId);

    /** Um registro por evento (a tentativa mais recente), do mais novo para o mais antigo. */
    List<LogRow> logs(UUID accountId, UUID endpointId, LogFilter filter, int limit, int offset);

    long countLogs(UUID accountId, UUID endpointId, LogFilter filter);

    Optional<LogDetail> logDetail(UUID accountId, UUID endpointId, UUID eventId);
}
