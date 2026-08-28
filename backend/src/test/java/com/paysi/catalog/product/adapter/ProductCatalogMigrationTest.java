package com.paysi.catalog.product.adapter;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.DriverManager;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class ProductCatalogMigrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("paysi")
            .withUsername("paysi")
            .withPassword("paysi");

    @Test
    void movesAffiliationFlagFromOffersToItsProduct() throws Exception {
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .target(MigrationVersion.fromVersion("40")).load().migrate();

        try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(), POSTGRES.getPassword());
             var statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO accounts (id,email,password_hash,full_name,person_type,tax_id)
                    VALUES ('11111111-1111-1111-1111-111111111111','migration@example.com','h',
                            'Migration','PF','52998224725')
                    """);
            statement.execute("""
                    INSERT INTO products (id,seller_id,name,segment,charge_type)
                    VALUES ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
                            '11111111-1111-1111-1111-111111111111','Produto','DIGITAL','ONE_TIME')
                    """);
            statement.execute("""
                    INSERT INTO offers
                      (id,product_id,charge_type,segment,slug,amount_cents,affiliates_enabled)
                    VALUES ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb',
                            'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa','IGNORED','IGNORED',
                            'produto-migration',2000,true)
                    """);
        }

        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .load().migrate();

        try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(), POSTGRES.getPassword())) {
            try (var statement = connection.prepareStatement(
                    "SELECT affiliation_enabled FROM products WHERE id = ?")) {
                statement.setObject(1, java.util.UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));
                try (var rows = statement.executeQuery()) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getBoolean(1)).isTrue();
                }
            }
            try (var statement = connection.prepareStatement("""
                    SELECT count(*)
                      FROM information_schema.columns
                     WHERE table_schema = 'public'
                       AND table_name = 'offers'
                       AND column_name = 'affiliates_enabled'
                    """)) {
                try (var rows = statement.executeQuery()) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getInt(1)).isZero();
                }
            }
            try (var statement = connection.prepareStatement("""
                    SELECT indexdef
                      FROM pg_indexes
                     WHERE schemaname = 'public'
                       AND indexname = 'idx_products_seller_created_id_active'
                    """)) {
                try (var rows = statement.executeQuery()) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getString(1))
                            .contains("seller_id, created_at DESC, id DESC")
                            .contains("WHERE (archived_at IS NULL)");
                }
            }
        }
    }
}
