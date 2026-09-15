package com.paysi.webhook.adapter;

import com.paysi.webhook.domain.OutboxEvent;
import com.paysi.webhook.domain.WebhookDelivery;
import com.paysi.webhook.domain.WebhookEndpoint;
import com.paysi.webhook.port.WebhookRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JdbcWebhookRepository.class)
@Testcontainers(disabledWithoutDocker = true)
class JdbcWebhookRepositoryIntegrationTest {
    private static final UUID ACCOUNT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Instant NOW = Instant.parse("2026-09-11T12:00:00Z");
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine").withDatabaseName("paysi").withUsername("paysi").withPassword("paysi");
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) { registry.add("spring.datasource.url", POSTGRES::getJdbcUrl); registry.add("spring.datasource.username", POSTGRES::getUsername); registry.add("spring.datasource.password", POSTGRES::getPassword); registry.add("spring.flyway.user", POSTGRES::getUsername); registry.add("spring.flyway.password", POSTGRES::getPassword); }

    @Autowired WebhookRepository repository;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach void account() { jdbc.update("insert into accounts(id,email,password_hash,full_name,person_type,tax_id) values (?,'webhook@example.com','hash','Webhook','PF','52998224725') on conflict do nothing", ACCOUNT); }

    @Test void scopesEndpointsAndClaimsEachOutboxEventOnce() {
        UUID endpointId = UUID.randomUUID();
        repository.insertEndpoint(new WebhookEndpoint(endpointId, ACCOUNT, "https://hooks.example.com", Set.of("PRODUCT.CHANGED"), true, new byte[]{1}, null, null, NOW));
        assertThat(repository.listEndpoints(ACCOUNT)).singleElement().satisfies(endpoint -> assertThat(endpoint.events()).containsExactly("PRODUCT.CHANGED"));
        assertThat(repository.listEndpoints(UUID.randomUUID())).isEmpty();

        UUID eventId = UUID.randomUUID();
        repository.insertOutbox(new OutboxEvent(eventId, ACCOUNT, "PRODUCT.CHANGED", "{\"productId\":\"one\"}", NOW));
        UUID firstWorker = UUID.randomUUID();
        assertThat(repository.claimOutbox(NOW, NOW.minusSeconds(300), firstWorker, 10)).extracting(OutboxEvent::id).containsExactly(eventId);
        assertThat(repository.claimOutbox(NOW, NOW.minusSeconds(300), UUID.randomUUID(), 10)).isEmpty();
        repository.markPublished(eventId, firstWorker, NOW);
        assertThat(repository.claimOutbox(NOW, NOW.minusSeconds(300), UUID.randomUUID(), 10)).isEmpty();
    }

    @Test void claimsOnlyLatestDueRetryAndKeepsOriginalEventId() {
        UUID endpointId = UUID.randomUUID(); UUID eventId = UUID.randomUUID();
        repository.insertEndpoint(new WebhookEndpoint(endpointId, ACCOUNT, "https://hooks.example.com", Set.of("SALE.PAID"), true, new byte[]{1}, null, null, NOW));
        repository.insertOutbox(new OutboxEvent(eventId, ACCOUNT, "SALE.PAID", "{}", NOW));
        repository.insertDelivery(new WebhookDelivery(UUID.randomUUID(), eventId, endpointId, 1, 500, "HTTP_500", NOW.minusSeconds(1), NOW));
        UUID token = UUID.randomUUID();
        var claims = repository.claimRetries(NOW, NOW.minusSeconds(300), token, 10);
        assertThat(claims).singleElement().satisfies(claim -> assertThat(claim.event().id()).isEqualTo(eventId));
        assertThat(repository.claimRetries(NOW, NOW.minusSeconds(300), UUID.randomUUID(), 10)).isEmpty();
    }

    @Test void databaseFactCreatesOutboxInTheSameTransaction() {
        UUID productId = UUID.randomUUID();
        jdbc.update("insert into products(id,seller_id,name,segment,charge_type) values (?,?,'Produto','DIGITAL','ONE_TIME')", productId, ACCOUNT);
        assertThat(jdbc.queryForObject("select count(*) from outbox_events where account_id=? and event_type='PRODUCT.CHANGED' and payload->>'productId'=?", Integer.class, ACCOUNT, productId.toString())).isEqualTo(1);
    }
}
