package com.paysi.catalog.product.adapter;

import com.paysi.catalog.product.domain.ChargeType;
import com.paysi.catalog.product.domain.Product;
import com.paysi.catalog.product.domain.Segment;
import com.paysi.catalog.product.port.ProductRepository;
import com.paysi.core.error.ConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaProductRepository.class)
@Testcontainers(disabledWithoutDocker = true)
class ProductRepositoryIntegrationTest {
    private static final UUID SELLER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER_SELLER = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final Instant NOW = Instant.parse("2026-08-28T12:00:00Z");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("paysi")
            .withUsername("paysi")
            .withPassword("paysi");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
    }

    @Autowired
    private ProductRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void accounts() {
        insertAccount(SELLER, "seller@example.com", "52998224725");
        insertAccount(OTHER_SELLER, "other@example.com", "11144477735");
    }

    @Test
    void persistsPaginatesByOwnerAndDoesNotCreateOffer() {
        Product older = product(SELLER, "Antigo", NOW.minusSeconds(1));
        Product newer = product(SELLER, "Novo", NOW);
        Product foreign = product(OTHER_SELLER, "Alheio", NOW.plusSeconds(1));
        repository.insert(older);
        repository.insert(newer);
        repository.insert(foreign);

        var first = repository.listActiveOwned(SELLER, null, 1);
        var second = repository.listActiveOwned(SELLER,
                new com.paysi.catalog.product.app.ProductCursor(first.getFirst().createdAt(),
                        first.getFirst().id()), 2);

        assertThat(first).extracting(Product::name).containsExactly("Novo");
        assertThat(second).extracting(Product::name).containsExactly("Antigo");
        assertThat(repository.findActiveOwned(SELLER, foreign.id())).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM offers", Long.class)).isZero();
    }

    @Test
    void archivesLogicallyAndExcludesResource() {
        Product stored = product(SELLER, "Produto", NOW);
        repository.insert(stored);

        assertThat(repository.archive(SELLER, stored.id(), NOW.plusSeconds(10))).isTrue();
        assertThat(repository.archive(SELLER, stored.id(), NOW.plusSeconds(20))).isFalse();
        assertThat(repository.findActiveOwned(SELLER, stored.id())).isEmpty();
        assertThat(repository.listActiveOwned(SELLER, null, 20)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT archived_at IS NOT NULL FROM products WHERE id = ?",
                Boolean.class, stored.id())).isTrue();
    }

    @Test
    void databaseRejectsInvalidName() {
        assertThatThrownBy(() -> jdbc.update("""
                        INSERT INTO products
                          (id, seller_id, name, description, segment, charge_type, affiliation_enabled)
                        VALUES (?, ?, ?, ?, 'DIGITAL', 'ONE_TIME', false)
                        """, UUID.randomUUID(), SELLER, "x".repeat(121), "Descrição"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsInvalidDescription() {
        assertThatThrownBy(() -> jdbc.update("""
                        INSERT INTO products
                          (id, seller_id, name, description, segment, charge_type, affiliation_enabled)
                        VALUES (?, ?, ?, ?, 'DIGITAL', 'ONE_TIME', false)
                        """, UUID.randomUUID(), SELLER, "Produto", "x".repeat(2001)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void translatesDatabaseImmutabilityGuardAfterOffer() {
        Product stored = product(SELLER, "Produto", NOW);
        repository.insert(stored);
        jdbc.update("""
                INSERT INTO offers (id, product_id, charge_type, segment, slug, amount_cents)
                VALUES (?, ?, 'IGNORED', 'IGNORED', ?, 2000)
                """, UUID.randomUUID(), stored.id(), "offer-" + stored.id());

        Product changed = stored.update("Produto", null, Segment.SAAS,
                ChargeType.SUBSCRIPTION, false);

        assertThatThrownBy(() -> repository.update(changed))
                .isInstanceOfSatisfying(ConflictException.class,
                        error -> assertThat(error.code()).isEqualTo("PRODUCT_CONTRACT_IMMUTABLE"));
    }

    private void insertAccount(UUID id, String email, String taxId) {
        jdbc.update("""
                INSERT INTO accounts (id, email, password_hash, full_name, person_type, tax_id)
                VALUES (?, ?, 'hash', 'Pessoa Teste', 'PF', ?)
                """, id, email, taxId);
    }

    private static Product product(UUID sellerId, String name, Instant createdAt) {
        return Product.createDraft(UUID.randomUUID(), sellerId, name, null, Segment.DIGITAL,
                ChargeType.ONE_TIME, false, createdAt);
    }
}
