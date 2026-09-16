package com.paysi.checkout.order.adapter;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.paysi.catalog.offer.domain.OfferPaymentMethod;
import com.paysi.checkout.order.domain.Buyer;
import com.paysi.checkout.order.domain.BuyerAddress;
import com.paysi.checkout.order.domain.Order;
import com.paysi.checkout.order.domain.OrderStatus;
import com.paysi.checkout.order.port.BuyerRepository;
import com.paysi.checkout.order.port.OrderRepository;
import com.paysi.identity.domain.PersonType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.dao.DataIntegrityViolationException;
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
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A idempotência do pedido não é uma checagem na aplicação: é o índice único
 * {@code uq_orders_idem (offer_id, idempotency_key)}. Este teste ataca o banco de
 * verdade, com conexões concorrentes, porque uma verificação em Java passaria mesmo
 * com o código errado.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class OrderIdempotencyIntegrationTest {
    private static final UUID SELLER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PRODUCT = UUID.fromString("99999999-9999-9999-9999-999999999999");
    private static final UUID OFFER = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final Instant NOW = Instant.parse("2026-09-16T12:00:00Z");
    private static final String KEY = "chave-do-navegador-1";
    private static final String HASH = "impressao-do-corpo";
    private static final int ATTEMPTS = 24;

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
    private OrderRepository orders;
    private BuyerRepository buyers;
    private UUID buyerId;

    @BeforeEach
    void fixtures() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        orders = new JdbcOrderRepository(jdbc);
        buyers = new JdbcBuyerRepository(jdbc, JsonMapper.builder().build());

        jdbc.update("DELETE FROM orders");
        jdbc.update("DELETE FROM buyers");
        jdbc.update("DELETE FROM offers");
        jdbc.update("DELETE FROM products");
        jdbc.update("""
                INSERT INTO accounts (id,email,password_hash,full_name,person_type,tax_id)
                VALUES (?,'seller@example.com','hash','Pessoa Teste','PF','52998224725')
                ON CONFLICT (id) DO NOTHING
                """, SELLER);
        jdbc.update("""
                INSERT INTO products (id,seller_id,name,segment,charge_type,affiliation_enabled)
                VALUES (?,?,'Produto','DIGITAL','ONE_TIME',false)
                """, PRODUCT, SELLER);
        jdbc.update("""
                INSERT INTO offers (id,product_id,charge_type,segment,slug,amount_cents,status)
                VALUES (?,?,'IGNORED','IGNORED','slug-pedido',17700,'PUBLISHED')
                """, OFFER, PRODUCT);

        buyerId = buyers.insertOrRead(buyer(), NOW).id();
    }

    @Test
    void compradorEhReutilizadoPeloParDocumentoEEmail() {
        Buyer again = buyers.insertOrRead(buyer(), NOW.plusSeconds(60));

        assertThat(again.id()).isEqualTo(buyerId);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM buyers", Integer.class)).isEqualTo(1);
    }

    @Test
    void enderecoDoCompradorSobreviveAoBancoEVolta() {
        Buyer stored = buyers.findActive("52998224725", "ana@example.com").orElseThrow();

        assertThat(stored.address()).isNotNull();
        assertThat(stored.address().city()).isEqualTo("São Paulo");
        assertThat(stored.address().state()).isEqualTo("SP");
    }

    @Test
    void repeticaoDaMesmaChaveNaoCriaSegundoPedido() {
        assertThat(orders.insertIfAbsent(order(UUID.randomUUID(), HASH))).isTrue();
        assertThat(orders.insertIfAbsent(order(UUID.randomUUID(), HASH))).isFalse();

        assertThat(jdbc.queryForObject("SELECT count(*) FROM orders", Integer.class)).isEqualTo(1);
        Order stored = orders.findByIdempotencyKey(OFFER, KEY).orElseThrow();
        assertThat(stored.requestHash()).isEqualTo(HASH);
        assertThat(stored.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(stored.paidCents()).isEqualTo(17_700);
    }

    @Test
    void requisicoesSimultaneasComAMesmaChaveCriamUmPedidoSo() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        List<Callable<Boolean>> attempts = IntStream.range(0, ATTEMPTS)
                .<Callable<Boolean>>mapToObj(i -> () -> {
                    start.await();
                    return orders.insertIfAbsent(order(UUID.randomUUID(), HASH));
                })
                .toList();

        ExecutorService pool = Executors.newFixedThreadPool(8);
        long created;
        try {
            List<Future<Boolean>> futures = attempts.stream().map(pool::submit).toList();
            start.countDown();
            pool.shutdown();
            assertThat(pool.awaitTermination(60, TimeUnit.SECONDS)).isTrue();

            created = 0;
            for (Future<Boolean> future : futures) {
                if (future.get()) created++;
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(created).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM orders", Integer.class)).isEqualTo(1);
    }

    @Test
    void chavesDiferentesNaMesmaOfertaCriamPedidosDiferentes() {
        assertThat(orders.insertIfAbsent(order(UUID.randomUUID(), HASH))).isTrue();

        Order outro = new Order(UUID.randomUUID(), OFFER, buyerId, null, "{\"name\":\"Ana\"}",
                17_700, 0, null, 17_700, OfferPaymentMethod.PIX, 1, OrderStatus.PENDING,
                "chave-do-navegador-2", HASH, NOW);
        assertThat(orders.insertIfAbsent(outro)).isTrue();

        assertThat(jdbc.queryForObject("SELECT count(*) FROM orders", Integer.class)).isEqualTo(2);
    }

    @Test
    void bancoRecusaPedidoAbaixoDoPisoTecnico() {
        Order barato = new Order(UUID.randomUUID(), OFFER, buyerId, null, "{\"name\":\"Ana\"}",
                17_700, 17_300, null, 400, OfferPaymentMethod.PIX, 1, OrderStatus.PENDING,
                KEY, HASH, NOW);

        assertThatThrownBy(() -> orders.insertIfAbsent(barato))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Order order(UUID id, String requestHash) {
        return new Order(id, OFFER, buyerId, null, "{\"name\":\"Ana\",\"termsHash\":\"h\"}",
                17_700, 0, null, 17_700, OfferPaymentMethod.PIX, 1, OrderStatus.PENDING,
                KEY, requestHash, NOW);
    }

    private static Buyer buyer() {
        return new Buyer(UUID.randomUUID(), "Ana Compradora", "ana@example.com", PersonType.PF,
                "52998224725", null, null,
                new BuyerAddress("01310-100", "Avenida Paulista", "1000", null, "Bela Vista",
                        "São Paulo", "SP"));
    }
}
