package com.paysi.affiliate.adapter;

import com.paysi.affiliate.domain.AffiliationEndReason;
import com.paysi.affiliate.domain.AffiliationRecurrence;
import com.paysi.affiliate.domain.AffiliationStatus;
import com.paysi.affiliate.port.AffiliateRepository;
import com.paysi.core.error.ConflictException;
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
import java.sql.Timestamp;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JdbcAffiliateRepository.class)
@Testcontainers(disabledWithoutDocker = true)
class JdbcAffiliateRepositoryIntegrationTest {
    private static final UUID SELLER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID AFFILIATE = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID OTHER = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID PRODUCT = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID DRAFT_PRODUCT = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final Instant NOW = Instant.parse("2026-09-10T12:00:00Z");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("paysi").withUsername("paysi").withPassword("paysi");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
    }

    @Autowired AffiliateRepository repository;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void fixtures() {
        account(SELLER, "seller@example.com", "52998224725");
        account(AFFILIATE, "affiliate@example.com", "11144477735");
        account(OTHER, "other@example.com", "12345678909");
        product(PRODUCT, SELLER, true);
        product(DRAFT_PRODUCT, SELLER, true);
        offer(PRODUCT, "PUBLISHED");
        offer(DRAFT_PRODUCT, "DRAFT");
    }

    @Test
    void marketplaceContainsOnlyAffiliableProductWithPublishedOffer() {
        assertThat(repository.listMarketplace(null, 20))
                .extracting(item -> item.productId()).containsExactly(PRODUCT);
        assertThat(repository.findMarketplaceProduct(DRAFT_PRODUCT)).isEmpty();
    }

    @Test
    void requestsApprovesAndKeepsApprovedCommissionImmutable() {
        UUID id = UUID.randomUUID();
        var requested = repository.request(id, PRODUCT, AFFILIATE, NOW);
        assertThat(requested.status()).isEqualTo(AffiliationStatus.PENDING);

        var approved = repository.approve(id, SELLER, 1_500,
                AffiliationRecurrence.ALL_CYCLES, NOW.plusSeconds(1)).orElseThrow();
        assertThat(approved.status()).isEqualTo(AffiliationStatus.APPROVED);
        assertThat(approved.commissionBps()).isEqualTo(1_500);
        assertThat(approved.recurrence()).isEqualTo(AffiliationRecurrence.ALL_CYCLES);

        assertThat(repository.approve(id, SELLER, 2_000,
                AffiliationRecurrence.FIRST_CHARGE, NOW.plusSeconds(2))).isEmpty();
        assertThat(jdbc.queryForObject("SELECT commission_bps FROM affiliations WHERE id = ?",
                Integer.class, id)).isEqualTo(1_500);
    }

    @Test
    void scopesSellerActionsAndMapsFraudEnding() {
        UUID id = UUID.randomUUID();
        repository.request(id, PRODUCT, AFFILIATE, NOW);
        assertThat(repository.approve(id, OTHER, 1_000, AffiliationRecurrence.FIRST_CHARGE, NOW)).isEmpty();
        repository.approve(id, SELLER, 1_000, AffiliationRecurrence.FIRST_CHARGE, NOW).orElseThrow();

        assertThat(repository.end(id, OTHER, AffiliationEndReason.FRAUD, NOW)).isEmpty();
        assertThat(repository.end(id, SELLER, AffiliationEndReason.FRAUD, NOW).orElseThrow().status())
                .isEqualTo(AffiliationStatus.FRAUD_ENDED);
    }

    @Test
    void duplicateActiveRequestHasStableConflict() {
        repository.request(UUID.randomUUID(), PRODUCT, AFFILIATE, NOW);
        assertThatThrownBy(() -> repository.request(UUID.randomUUID(), PRODUCT, AFFILIATE, NOW))
                .isInstanceOfSatisfying(ConflictException.class,
                        error -> assertThat(error.code()).isEqualTo("AFFILIATION_ALREADY_ACTIVE"));
    }

    private void account(UUID id, String email, String taxId) {
        jdbc.update("""
                INSERT INTO accounts (id,email,password_hash,full_name,person_type,tax_id,kyc_status)
                VALUES (?,?,'hash','Pessoa Teste','PF',?,'APPROVED')
                """, id, email, taxId);
    }

    private void product(UUID id, UUID seller, boolean affiliationEnabled) {
        jdbc.update("""
                INSERT INTO products (id,seller_id,name,segment,charge_type,affiliation_enabled,status,created_at)
                VALUES (?,?,'Produto','DIGITAL','ONE_TIME',?,'ACTIVE',?)
                """, id, seller, affiliationEnabled, Timestamp.from(NOW));
    }

    private void offer(UUID productId, String status) {
        jdbc.update("""
                INSERT INTO offers (id,product_id,charge_type,segment,slug,amount_cents,status)
                VALUES (?,?,'IGNORED','IGNORED',?,10000,?)
                """, UUID.randomUUID(), productId, "slug-" + productId, status);
    }
}
