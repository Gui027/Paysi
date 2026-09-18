package com.paysi.observability.alert.adapter;

import com.paysi.observability.alert.domain.AlertEvent;
import com.paysi.observability.alert.port.AlertRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;

@Repository
class JdbcAlertRepository implements AlertRepository {
    private final JdbcTemplate jdbc;

    JdbcAlertRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(AlertEvent event) {
        jdbc.update("insert into ops_alerts(id,alert_type,severity,payload,created_at) "
                        + "values (?,?,?,cast(? as jsonb),?)",
                event.id(), event.type(), event.severity(), event.payload(),
                Timestamp.from(event.createdAt()));
    }
}
