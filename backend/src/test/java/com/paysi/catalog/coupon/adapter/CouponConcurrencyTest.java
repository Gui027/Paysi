package com.paysi.catalog.coupon.adapter;

import com.paysi.catalog.coupon.port.CouponRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O teto do cupom é do banco, não da aplicação: cem resgates simultâneos de um cupom
 * com cinquenta unidades resultam em exatamente cinquenta (documento 6, §8).
 *
 * <p>Cada thread usa a própria conexão em auto-commit — o pool da aplicação roda com
 * {@code auto-commit: false}, e um teste que compartilhasse a transação do JUnit
 * serializaria as escritas e nunca falharia com o código errado.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class CouponConcurrencyTest {
    private static final UUID SELLER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OFFER = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID COUPON = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
    private static final Instant NOW = Instant.parse("2026-09-16T12:00:00Z");
    private static final int ATTEMPTS = 100;
    private static final int STOCK = 50;

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

    private JdbcTemplate jdbc;
    private CouponRepository repository;

    @BeforeEach
    void fixtures() {
        // Conexões próprias, fora da transação do teste, para que a corrida seja real.
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        repository = new JdbcCouponRepository(jdbc);

        jdbc.update("DELETE FROM coupon_redemptions");
        jdbc.update("DELETE FROM coupon_offers");
        jdbc.update("DELETE FROM coupons");
        jdbc.update("DELETE FROM offers");
        jdbc.update("DELETE FROM products");

        // A conta permanece entre os testes: um gatilho cria a assinatura de plataforma
        // junto com ela, e apagá-la esbarraria na chave estrangeira.
        jdbc.update("""
                INSERT INTO accounts (id,email,password_hash,full_name,person_type,tax_id)
                VALUES (?,?,'hash','Pessoa Teste','PF','52998224725')
                ON CONFLICT (id) DO NOTHING
                """, SELLER, "seller@example.com");
        UUID product = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO products (id,seller_id,name,segment,charge_type,affiliation_enabled)
                VALUES (?,?,'Produto','DIGITAL','ONE_TIME',false)
                """, product, SELLER);
        jdbc.update("""
                INSERT INTO offers (id,product_id,charge_type,segment,slug,amount_cents)
                VALUES (?,?,'IGNORED','IGNORED','slug-concorrencia',2000)
                """, OFFER, product);
        jdbc.update("""
                INSERT INTO coupons (id,seller_id,code,kind,value,max_redemptions,max_per_buyer,redeemed_count)
                VALUES (?,?,'PROMO50','PERCENT',1000,?,1,0)
                """, COUPON, SELLER, STOCK);
        jdbc.update("INSERT INTO coupon_offers (coupon_id,offer_id) VALUES (?,?)", COUPON, OFFER);
    }

    @Test
    void cemResgatesSimultaneosConsomemExatamenteOEstoque() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        List<Callable<Boolean>> attempts = java.util.stream.IntStream.range(0, ATTEMPTS)
                .<Callable<Boolean>>mapToObj(i -> () -> {
                    start.await();
                    return repository.reserve(COUPON, NOW);
                })
                .toList();

        ExecutorService pool = Executors.newFixedThreadPool(16);
        try {
            List<Future<Boolean>> futures = attempts.stream().map(pool::submit).toList();
            start.countDown();
            pool.shutdown();
            assertThat(pool.awaitTermination(60, TimeUnit.SECONDS)).isTrue();

            long granted = 0;
            for (Future<Boolean> future : futures) {
                if (future.get()) granted++;
            }
            assertThat(granted).isEqualTo(STOCK);
        } finally {
            pool.shutdownNow();
        }

        assertThat(jdbc.queryForObject("SELECT redeemed_count FROM coupons WHERE id = ?",
                Integer.class, COUPON)).isEqualTo(STOCK);
    }

    @Test
    void cupomVencidoNaoIncrementa() {
        jdbc.update("UPDATE coupons SET expires_at = ? WHERE id = ?",
                java.sql.Timestamp.from(NOW.minusSeconds(1)), COUPON);

        assertThat(repository.reserve(COUPON, NOW)).isFalse();
        assertThat(jdbc.queryForObject("SELECT redeemed_count FROM coupons WHERE id = ?",
                Integer.class, COUPON)).isZero();
    }

    @Test
    void cupomArquivadoNaoIncrementa() {
        jdbc.update("UPDATE coupons SET archived_at = ? WHERE id = ?",
                java.sql.Timestamp.from(NOW), COUPON);

        assertThat(repository.reserve(COUPON, NOW)).isFalse();
    }
}
