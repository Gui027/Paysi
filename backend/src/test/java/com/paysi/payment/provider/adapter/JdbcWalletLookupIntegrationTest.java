package com.paysi.payment.provider.adapter;

import com.paysi.payment.provider.WalletLookup;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JdbcWalletLookup.class)
@Testcontainers(disabledWithoutDocker = true)
class JdbcWalletLookupIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine").withDatabaseName("paysi").withUsername("paysi").withPassword("paysi");
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) { registry.add("spring.datasource.url", POSTGRES::getJdbcUrl); registry.add("spring.datasource.username", POSTGRES::getUsername); registry.add("spring.datasource.password", POSTGRES::getPassword); registry.add("spring.flyway.user", POSTGRES::getUsername); registry.add("spring.flyway.password", POSTGRES::getPassword); }

    @Autowired WalletLookup lookup;
    @Autowired JdbcTemplate jdbc;

    @Test
    void findsTheWalletOfAnAccountWithASubaccount() {
        UUID accountId = UUID.randomUUID();
        jdbc.update("insert into accounts(id,email,password_hash,full_name,person_type,tax_id,provider_account_id) "
                + "values (?,'wallet@example.com','hash','Vendedor','PF','52998224725','wallet_abc')", accountId);

        assertThat(lookup.walletId(accountId)).contains("wallet_abc");
    }

    @Test
    void hasNoWalletWhenTheAccountNeverGotASubaccountOrDoesNotExist() {
        UUID accountId = UUID.randomUUID();
        jdbc.update("insert into accounts(id,email,password_hash,full_name,person_type,tax_id) "
                + "values (?,'no-wallet@example.com','hash','Vendedor','PF','52998224725')", accountId);

        assertThat(lookup.walletId(accountId)).isEmpty();
        assertThat(lookup.walletId(UUID.randomUUID())).isEmpty();
    }
}
