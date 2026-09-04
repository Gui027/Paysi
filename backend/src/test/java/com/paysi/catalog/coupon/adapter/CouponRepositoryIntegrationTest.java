package com.paysi.catalog.coupon.adapter;

import com.paysi.catalog.coupon.domain.Coupon;
import com.paysi.catalog.coupon.domain.CouponKind;
import com.paysi.catalog.coupon.domain.CouponValues;
import com.paysi.catalog.coupon.port.CouponRepository;
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
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JdbcCouponRepository.class)
@Testcontainers(disabledWithoutDocker = true)
class CouponRepositoryIntegrationTest {
    private static final UUID SELLER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID OFFER_A = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID OFFER_B = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final Instant NOW = Instant.parse("2026-09-02T12:00:00Z");

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

    @Autowired CouponRepository repository;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void fixtures() {
        account(SELLER, "seller@example.com", "52998224725");
        account(OTHER, "other@example.com", "11144477735");
        product(OFFER_A, SELLER);
        product(OFFER_B, SELLER);
    }

    @Test
    void persistsOfferLinksAndScopesByOwner() {
        Coupon coupon = Coupon.create(UUID.randomUUID(), SELLER, "PROMO10",
                new CouponValues(CouponKind.PERCENT, 1_000, null, null, 100, 2, Set.of(OFFER_A, OFFER_B)),
                NOW);
        repository.insert(coupon);

        Coupon stored = repository.findActiveOwned(SELLER, coupon.id()).orElseThrow();
        assertThat(stored.offerIds()).containsExactlyInAnyOrder(OFFER_A, OFFER_B);
        assertThat(stored.value()).isEqualTo(1_000);
        assertThat(repository.findActiveOwned(OTHER, coupon.id())).isEmpty();
        assertThat(repository.listActiveOwned(SELLER)).hasSize(1);
    }

    @Test
    void rejectsDuplicateActiveCodeForSameSeller() {
        Coupon first = Coupon.create(UUID.randomUUID(), SELLER, "PROMO10",
                new CouponValues(CouponKind.PERCENT, 1_000, null, null, null, 1, Set.of(OFFER_A)), NOW);
        repository.insert(first);

        Coupon duplicate = Coupon.create(UUID.randomUUID(), SELLER, "PROMO10",
                new CouponValues(CouponKind.FIXED, 500, null, null, null, 1, Set.of(OFFER_A)), NOW);
        assertThatThrownBy(() -> repository.insert(duplicate))
                .isInstanceOfSatisfying(ConflictException.class,
                        error -> assertThat(error.code()).isEqualTo("COUPON_CODE_TAKEN"));
    }

    @Test
    void updatesReplacesOffersAndArchivingPreservesRow() {
        Coupon coupon = Coupon.create(UUID.randomUUID(), SELLER, "PROMO10",
                new CouponValues(CouponKind.PERCENT, 1_000, null, null, null, 1, Set.of(OFFER_A)), NOW);
        repository.insert(coupon);

        Coupon changed = coupon.update(new CouponValues(CouponKind.FIXED, 500, null, null, 10, 3,
                Set.of(OFFER_B)));
        repository.update(changed);

        Coupon stored = repository.findActiveOwned(SELLER, coupon.id()).orElseThrow();
        assertThat(stored.kind()).isEqualTo(CouponKind.FIXED);
        assertThat(stored.value()).isEqualTo(500);
        assertThat(stored.offerIds()).containsExactly(OFFER_B);

        assertThat(repository.archive(OTHER, coupon.id(), NOW.plusSeconds(1))).isFalse();
        assertThat(repository.archive(SELLER, coupon.id(), NOW.plusSeconds(1))).isTrue();
        assertThat(repository.findActiveOwned(SELLER, coupon.id())).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM coupons WHERE id = ?", Integer.class,
                coupon.id())).isEqualTo(1);
    }

    @Test
    void databaseCapsRedeemedCountAtMaxRedemptions() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO coupons (id, seller_id, code, kind, value, max_redemptions, redeemed_count)
                VALUES (?, ?, 'ESTOURADO', 'PERCENT', 1000, 1, 2)
                """, UUID.randomUUID(), SELLER)).isInstanceOf(org.springframework.dao.DataAccessException.class);
    }

    private void product(UUID offerId, UUID seller) {
        UUID product = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO products (id,seller_id,name,segment,charge_type,affiliation_enabled)
                VALUES (?,?,'Produto','DIGITAL','ONE_TIME',false)
                """, product, seller);
        jdbc.update("""
                INSERT INTO offers (id,product_id,charge_type,segment,slug,amount_cents)
                VALUES (?,?,'IGNORED','IGNORED',?,2000)
                """, offerId, product, "slug-" + offerId);
    }

    private void account(UUID id, String email, String taxId) {
        jdbc.update("""
                INSERT INTO accounts (id,email,password_hash,full_name,person_type,tax_id)
                VALUES (?,?,'hash','Pessoa Teste','PF',?)
                """, id, email, taxId);
    }
}
