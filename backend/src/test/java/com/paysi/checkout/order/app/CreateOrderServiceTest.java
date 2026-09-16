package com.paysi.checkout.order.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.paysi.catalog.coupon.app.CouponDiscount;
import com.paysi.catalog.coupon.app.CouponRedemptionService;
import com.paysi.catalog.offer.domain.Offer;
import com.paysi.catalog.offer.domain.OfferPaymentMethod;
import com.paysi.catalog.offer.domain.OfferPayoutDelay;
import com.paysi.catalog.offer.domain.OfferStatus;
import com.paysi.catalog.offer.port.OfferRepository;
import com.paysi.catalog.product.domain.ChargeType;
import com.paysi.catalog.product.domain.Segment;
import com.paysi.checkout.order.domain.Buyer;
import com.paysi.checkout.order.domain.BuyerAddress;
import com.paysi.checkout.order.domain.Order;
import com.paysi.checkout.order.domain.OrderStatus;
import com.paysi.checkout.order.port.AffiliationClickLookup;
import com.paysi.checkout.order.port.BuyerRepository;
import com.paysi.checkout.order.port.IdempotencyLock;
import com.paysi.checkout.order.port.OrderRepository;
import com.paysi.checkout.pricing.app.PriceQuote;
import com.paysi.checkout.pricing.app.PriceSimulationService;
import com.paysi.core.error.ConflictException;
import com.paysi.core.error.NotFoundException;
import com.paysi.core.error.ValidationException;
import com.paysi.identity.domain.PersonType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CreateOrderServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-16T12:00:00Z");
    private static final String SLUG = "curso-de-teste";
    private static final String KEY = "chave-do-navegador-1";
    private static final UUID OFFER = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID PRODUCT = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID BUYER = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final String CPF = "52998224725";
    private static final String CNPJ = "11222333000181";

    private OfferRepository offers;
    private OrderRepository orders;
    private BuyerRepository buyers;
    private PriceSimulationService prices;
    private CouponRedemptionService coupons;
    private AffiliationClickLookup clicks;
    private IdempotencyLock locks;
    private CreateOrderService service;

    @BeforeEach
    void setUp() {
        offers = mock(OfferRepository.class);
        orders = mock(OrderRepository.class);
        buyers = mock(BuyerRepository.class);
        prices = mock(PriceSimulationService.class);
        coupons = mock(CouponRedemptionService.class);
        clicks = mock(AffiliationClickLookup.class);
        locks = mock(IdempotencyLock.class);

        when(offers.findPublishedBySlug(SLUG)).thenReturn(Optional.of(offer(Segment.DIGITAL)));
        when(locks.acquire(anyString(), anyString(), anyString(), any(Duration.class))).thenReturn(true);
        when(buyers.insertOrRead(any(), any())).thenAnswer(call -> {
            Buyer buyer = call.getArgument(0);
            return new Buyer(BUYER, buyer.name(), buyer.email(), buyer.personType(), buyer.taxId(),
                    buyer.legalName(), buyer.municipalReg(), buyer.address());
        });
        when(prices.priceFor(any(), any(), anyInt(), any(), anyInt())).thenReturn(quote(CouponDiscount.none()));
        when(orders.insertIfAbsent(any())).thenReturn(true);
        when(clicks.findLastClick(anyString(), any())).thenReturn(Optional.empty());

        service = new CreateOrderService(offers, orders, buyers, prices, coupons, clicks, locks,
                objectMapper(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void criaPedidoPendenteComORetratoDoComprador() {
        OrderResult result = service.create(SLUG, KEY, command(PersonType.PF, CPF, null, null));

        assertThat(result.replay()).isFalse();
        Order order = captured();
        assertThat(order.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.offerId()).isEqualTo(OFFER);
        assertThat(order.buyerId()).isEqualTo(BUYER);
        assertThat(order.grossCents()).isEqualTo(17_700);
        assertThat(order.paidCents()).isEqualTo(17_700);
        assertThat(order.idempotencyKey()).isEqualTo(KEY);
        assertThat(order.buyerSnapshot())
                .contains("\"termsHash\":\"hash-dos-termos\"")
                .contains("\"taxId\":\"" + CPF + "\"");
    }

    @Test
    void chaveDeIdempotenciaEObrigatoria() {
        assertThatThrownBy(() -> service.create(SLUG, "  ", command(PersonType.PF, CPF, null, null)))
                .isInstanceOfSatisfying(ValidationException.class, error -> {
                    assertThat(error.code()).isEqualTo("IDEMPOTENCY_KEY_REQUIRED");
                    assertThat(error.field()).isEqualTo("Idempotency-Key");
                });
    }

    @Test
    void ofertaNaoPublicadaNaoCriaPedido() {
        when(offers.findPublishedBySlug("rascunho")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create("rascunho", KEY, command(PersonType.PF, CPF, null, null)))
                .isInstanceOfSatisfying(NotFoundException.class,
                        error -> assertThat(error.code()).isEqualTo("OFFER_NOT_FOUND"));
        verify(orders, never()).insertIfAbsent(any());
    }

    @Test
    void mesmaChaveComCorpoDiferenteEhRecusadaPelaGuardaRapida() {
        when(locks.acquire(anyString(), anyString(), anyString(), any(Duration.class))).thenReturn(false);
        when(locks.requestHashOf(anyString(), eq(KEY))).thenReturn(Optional.of("impressao-de-outro-corpo"));

        assertThatThrownBy(() -> service.create(SLUG, KEY, command(PersonType.PF, CPF, null, null)))
                .isInstanceOfSatisfying(ConflictException.class,
                        error -> assertThat(error.code()).isEqualTo("IDEMPOTENCY_KEY_REUSED"));
        verify(orders, never()).insertIfAbsent(any());
    }

    @Test
    void mesmaChaveComMesmoCorpoDevolveOPedidoOriginal() {
        when(orders.insertIfAbsent(any())).thenReturn(false);
        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);

        // Primeiro descobre a impressão que o serviço calcula, depois devolve o pedido
        // original com a mesma impressão.
        assertThatThrownBy(() -> service.create(SLUG, KEY, command(PersonType.PF, CPF, null, null)))
                .isInstanceOf(ConflictException.class);
        verify(orders).insertIfAbsent(captor.capture());
        String hash = captor.getValue().requestHash();

        when(orders.findByIdempotencyKey(OFFER, KEY))
                .thenReturn(Optional.of(existing(hash, 17_700)));

        OrderResult result = service.create(SLUG, KEY, command(PersonType.PF, CPF, null, null));

        assertThat(result.replay()).isTrue();
        assertThat(result.order().paidCents()).isEqualTo(17_700);
    }

    @Test
    void pedidoGravadoNoBancoComOutraImpressaoEh409() {
        when(orders.insertIfAbsent(any())).thenReturn(false);
        when(orders.findByIdempotencyKey(OFFER, KEY))
                .thenReturn(Optional.of(existing("impressao-de-outro-corpo", 17_700)));

        assertThatThrownBy(() -> service.create(SLUG, KEY, command(PersonType.PF, CPF, null, null)))
                .isInstanceOfSatisfying(ConflictException.class,
                        error -> assertThat(error.code()).isEqualTo("IDEMPOTENCY_KEY_REUSED"));
    }

    @Test
    void repeticaoNaoConsomeUnidadeDeCupom() {
        when(prices.priceFor(any(), any(), anyInt(), any(), anyInt()))
                .thenReturn(quote(new CouponDiscount(UUID.randomUUID(), "PROMO10", 1_770, 1)));
        when(orders.insertIfAbsent(any())).thenReturn(false);
        when(orders.findByIdempotencyKey(any(), anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(SLUG, KEY, command(PersonType.PF, CPF, null, "PROMO10")))
                .isInstanceOf(ConflictException.class);

        verify(coupons, never()).reserveUnit(any());
        verify(coupons, never()).confirm(any(), any(), any());
    }

    @Test
    void cupomSoEhConsumidoDepoisQueOPedidoExiste() {
        CouponDiscount discount = new CouponDiscount(UUID.randomUUID(), "PROMO10", 1_770, 1);
        when(prices.priceFor(any(), any(), anyInt(), any(), anyInt())).thenReturn(quote(discount));

        service.create(SLUG, KEY, command(PersonType.PF, CPF, null, "PROMO10"));

        var order = captured();
        assertThat(order.couponId()).isEqualTo(discount.couponId());
        assertThat(order.discountCents()).isEqualTo(1_770);
        assertThat(order.paidCents()).isEqualTo(15_930);
        verify(coupons).reserveUnit(discount);
        verify(coupons).confirm(discount, order.id(), BUYER);
    }

    @Test
    void aceiteDosTermosEhObrigatorio() {
        CreateOrderCommand semAceite = new CreateOrderCommand("Ana", "ana@example.com",
                PersonType.PF, CPF, null, null, null, OfferPaymentMethod.PIX, 1, null, null, null, null);

        assertThatThrownBy(() -> service.create(SLUG, KEY, semAceite))
                .isInstanceOfSatisfying(ValidationException.class,
                        error -> assertThat(error.code()).isEqualTo("TERMS_NOT_ACCEPTED"));
    }

    @Test
    void cartaoSemTokenEhRecusado() {
        CreateOrderCommand semToken = new CreateOrderCommand("Ana", "ana@example.com",
                PersonType.PF, CPF, null, null, null, OfferPaymentMethod.CARD, 1, null, null, null,
                "hash-dos-termos");

        assertThatThrownBy(() -> service.create(SLUG, KEY, semToken))
                .isInstanceOfSatisfying(ValidationException.class,
                        error -> assertThat(error.code()).isEqualTo("CARD_TOKEN_REQUIRED"));
    }

    @Test
    void compradorPjExigeRazaoSocialEEndereco() {
        CreateOrderCommand pjIncompleto = command(PersonType.PJ, CNPJ, null, null);

        assertThatThrownBy(() -> service.create(SLUG, KEY, pjIncompleto))
                .isInstanceOfSatisfying(ValidationException.class, error -> {
                    assertThat(error.code()).isEqualTo("BUYER_INVALID");
                    assertThat(error.field()).isEqualTo("legalName");
                });
    }

    @Test
    void ofertaSaasExigeDadosFiscaisAteDeCompradorPf() {
        when(offers.findPublishedBySlug(SLUG)).thenReturn(Optional.of(offer(Segment.SAAS)));

        assertThatThrownBy(() -> service.create(SLUG, KEY, command(PersonType.PF, CPF, null, null)))
                .isInstanceOfSatisfying(ValidationException.class,
                        error -> assertThat(error.code()).isEqualTo("BUYER_INVALID"));
    }

    @Test
    void compradorPjCompletoPassa() {
        service.create(SLUG, KEY, command(PersonType.PJ, CNPJ, endereco(), null));

        assertThat(captured().buyerSnapshot()).contains("\"legalName\":\"Empresa Teste LTDA\"");
    }

    @Test
    void documentoInvalidoEhRecusado() {
        assertThatThrownBy(() -> service.create(SLUG, KEY, command(PersonType.PF, "11111111111", null, null)))
                .isInstanceOfSatisfying(ValidationException.class,
                        error -> assertThat(error.code()).isEqualTo("INVALID_TAX_ID"));
    }

    @Test
    void semVisitorKeyOPedidoNasceSemAfiliado() {
        service.create(SLUG, KEY, command(PersonType.PF, CPF, null, null));

        assertThat(captured().affiliationId()).isNull();
        verify(clicks, never()).findLastClick(anyString(), any());
    }

    @Test
    void ultimoCliqueValidoAtribuiAfiliadoEComissao() {
        UUID affiliation = UUID.randomUUID();
        when(clicks.findLastClick("visitante-1", PRODUCT))
                .thenReturn(Optional.of(new AffiliationClickLookup.Attribution(affiliation, 1_000)));

        CreateOrderCommand comVisitante = new CreateOrderCommand("Ana", "ana@example.com",
                PersonType.PF, CPF, null, null, null, OfferPaymentMethod.PIX, 1, null, null,
                "visitante-1", "hash-dos-termos");
        service.create(SLUG, KEY, comVisitante);

        assertThat(captured().affiliationId()).isEqualTo(affiliation);
        verify(prices).priceFor(any(), eq(OfferPaymentMethod.PIX), eq(1), any(), eq(1_000));
    }

    @Test
    void comandoNaoExpoePiiNemTokenEmLog() {
        CreateOrderCommand comando = new CreateOrderCommand("Ana", "ana@example.com",
                PersonType.PF, CPF, null, null, null, OfferPaymentMethod.CARD, 1, "tok_visivel",
                null, "visitante-1", "hash-dos-termos");

        assertThat(comando.toString())
                .doesNotContain("Ana")
                .doesNotContain("ana@example.com")
                .doesNotContain(CPF)
                .doesNotContain("tok_visivel")
                .doesNotContain("visitante-1")
                .contains("[REDACTED]");
    }

    /** Mesma configuração que o Spring Boot entrega: datas em ISO-8601, não em número. */
    private static ObjectMapper objectMapper() {
        return JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
    }

    private Order captured() {
        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orders).insertIfAbsent(captor.capture());
        return captor.getValue();
    }

    private static PriceQuote quote(CouponDiscount discount) {
        long paid = 17_700 - discount.discountCents();
        return new PriceQuote(17_700, discount.discountCents(), paid, OfferPaymentMethod.PIX, 1,
                906, 0, paid - 906, NOW.plus(Duration.ofDays(15)), discount);
    }

    private static Order existing(String requestHash, long paidCents) {
        return new Order(UUID.randomUUID(), OFFER, BUYER, null, "{\"name\":\"Ana\"}", paidCents, 0,
                null, paidCents, OfferPaymentMethod.PIX, 1, OrderStatus.PENDING, KEY, requestHash,
                NOW.minusSeconds(30));
    }

    private static CreateOrderCommand command(PersonType personType, String taxId,
            BuyerAddress address, String coupon) {
        String legalName = address == null ? null : "Empresa Teste LTDA";
        return new CreateOrderCommand("Ana Compradora", "ana@example.com", personType, taxId,
                legalName, null, address, OfferPaymentMethod.PIX, 1, null, coupon, null,
                "hash-dos-termos");
    }

    private static BuyerAddress endereco() {
        return new BuyerAddress("01310-100", "Avenida Paulista", "1000", null, "Bela Vista",
                "São Paulo", "sp");
    }

    private static Offer offer(Segment segment) {
        return new Offer(OFFER, PRODUCT, ChargeType.ONE_TIME, segment, SLUG, 17_700, null, 0, true,
                7, 12, 3, 5, Set.of(OfferPaymentMethod.PIX, OfferPaymentMethod.CARD),
                OfferPayoutDelay.D15, OfferStatus.PUBLISHED, null, NOW.minusSeconds(60),
                NOW.minusSeconds(60));
    }
}
