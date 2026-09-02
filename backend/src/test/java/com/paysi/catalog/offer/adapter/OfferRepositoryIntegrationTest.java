package com.paysi.catalog.offer.adapter;

import com.paysi.catalog.offer.domain.BillingCycle;
import com.paysi.catalog.offer.domain.Offer;
import com.paysi.catalog.offer.domain.OfferPaymentMethod;
import com.paysi.catalog.offer.domain.OfferPayoutDelay;
import com.paysi.catalog.offer.domain.OfferValues;
import com.paysi.catalog.offer.port.OfferRepository;
import com.paysi.catalog.product.domain.ChargeType;
import com.paysi.catalog.product.domain.Segment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
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
@Import(JdbcOfferRepository.class)
@Testcontainers(disabledWithoutDocker = true)
class OfferRepositoryIntegrationTest {
    private static final UUID SELLER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID PRODUCT = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
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

    @Autowired OfferRepository repository;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void fixtures() {
        account(SELLER, "seller@example.com", "52998224725");
        account(OTHER, "other@example.com", "11144477735");
        jdbc.update("""
                INSERT INTO products (id,seller_id,name,segment,charge_type,affiliation_enabled)
                VALUES (?,?,'SaaS','SAAS','SUBSCRIPTION',false)
                """, PRODUCT, SELLER);
    }

    @Test
    void persistsDenormalizedContractMethodsAndScopesByOwner() {
        Offer offer = offer();
        repository.insert(offer);

        Offer stored = repository.findActiveOwned(SELLER, offer.id()).orElseThrow();
        assertThat(stored.segment()).isEqualTo(Segment.SAAS);
        assertThat(stored.chargeType()).isEqualTo(ChargeType.SUBSCRIPTION);
        assertThat(stored.paymentMethods()).containsExactlyInAnyOrder(OfferPaymentMethod.CARD,
                OfferPaymentMethod.BOLETO);
        assertThat(stored.payoutDelay()).isEqualTo(OfferPayoutDelay.D7);
        assertThat(repository.findActiveOwned(OTHER, offer.id())).isEmpty();
        assertThat(repository.listActiveOwned(SELLER, PRODUCT)).hasSize(1);
    }

    @Test
    void keepsSlugOnUpdateAndArchivesLogically() {
        Offer offer = offer();
        repository.insert(offer);
        Offer changed = offer.update(new OfferValues(20_000, BillingCycle.ANNUAL, 0, true,
                14, 6, 5, 7, Set.of(OfferPaymentMethod.CARD), OfferPayoutDelay.D15), NOW.plusSeconds(1));
        repository.update(changed);

        Offer stored = repository.findActiveOwned(SELLER, offer.id()).orElseThrow();
        assertThat(stored.slug()).isEqualTo(offer.slug());
        assertThat(stored.priceCents()).isEqualTo(20_000);
        assertThat(stored.paymentMethods()).containsExactly(OfferPaymentMethod.CARD);
        assertThat(repository.archive(OTHER, offer.id(), NOW.plusSeconds(2))).isFalse();
        assertThat(repository.archive(SELLER, offer.id(), NOW.plusSeconds(2))).isTrue();
        assertThat(repository.findActiveOwned(SELLER, offer.id())).isEmpty();
    }

    @Test
    void databaseRejectsPriceBelowMinimum() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO offers (id,product_id,charge_type,segment,slug,amount_cents,cycle)
                VALUES (?,?,'IGNORED','IGNORED','baixo',1999,'MONTHLY')
                """, UUID.randomUUID(), PRODUCT)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsBoletoForDigitalProduct() {
        UUID offerId = digitalOffer();
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO offer_payment_methods (offer_id,method) VALUES (?,'BOLETO')
                """, offerId)).isInstanceOf(DataAccessException.class)
                .hasMessageContaining("Boleto disponivel apenas no segmento SAAS");
    }

    private UUID digitalOffer() {
        UUID product = UUID.randomUUID();
        UUID offer = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO products (id,seller_id,name,segment,charge_type,affiliation_enabled)
                VALUES (?,?,'Digital','DIGITAL','ONE_TIME',false)
                """, product, SELLER);
        jdbc.update("""
                INSERT INTO offers (id,product_id,charge_type,segment,slug,amount_cents)
                VALUES (?,?,'IGNORED','IGNORED',?,2000)
                """, offer, product, "digital-" + offer);
        return offer;
    }

    private static Offer offer() {
        return Offer.create(UUID.randomUUID(), PRODUCT, ChargeType.SUBSCRIPTION, Segment.SAAS,
                "saas-12345678", new OfferValues(10_000, BillingCycle.MONTHLY, 0, true, 7, 12,
                        3, 5, Set.of(OfferPaymentMethod.CARD, OfferPaymentMethod.BOLETO),
                        OfferPayoutDelay.D7), NOW);
    }

    private void account(UUID id, String email, String taxId) {
        jdbc.update("""
                INSERT INTO accounts (id,email,password_hash,full_name,person_type,tax_id)
                VALUES (?,?,'hash','Pessoa Teste','PF',?)
                """, id, email, taxId);
    }
}
