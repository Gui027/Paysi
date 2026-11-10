package com.paysi.apikey.web;

import com.paysi.apikey.app.ApiAuthService;
import com.paysi.apikey.app.ApiKeyModels;
import com.paysi.catalog.offer.domain.Offer;
import com.paysi.catalog.offer.domain.OfferPaymentMethod;
import com.paysi.catalog.offer.port.OfferRepository;
import com.paysi.catalog.product.app.ProductService;
import com.paysi.checkout.charge.app.CardTokenService;
import com.paysi.checkout.charge.app.ChargeCreationService;
import com.paysi.checkout.charge.app.ChargeStartResult;
import com.paysi.checkout.charge.app.StartChargeCommand;
import com.paysi.checkout.charge.port.ChargeCreationRepository;
import com.paysi.checkout.order.app.CreateOrderCommand;
import com.paysi.checkout.order.app.CreateOrderService;
import com.paysi.checkout.order.app.OrderResult;
import com.paysi.checkout.order.domain.Order;
import com.paysi.checkout.order.domain.OrderStatus;
import com.paysi.core.error.NotFoundException;
import com.paysi.identity.domain.PersonType;
import com.paysi.payment.pix.domain.PixResult;
import com.paysi.security.ratelimit.app.CheckoutRateLimitGuard;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PublicPaymentControllerTest {
    private static final UUID SELLER = UUID.randomUUID();
    private final ApiAuthService auth = mock(ApiAuthService.class);
    private final OfferRepository offers = mock(OfferRepository.class);
    private final ProductService products = mock(ProductService.class);
    private final CreateOrderService orders = mock(CreateOrderService.class);
    private final ChargeCreationService charges = mock(ChargeCreationService.class);
    private final CardTokenService cards = mock(CardTokenService.class);
    private final CheckoutRateLimitGuard rateLimit = mock(CheckoutRateLimitGuard.class);
    private PublicPaymentController controller;

    @BeforeEach
    void setUp() {
        controller = new PublicPaymentController(auth, offers, products, orders, charges, cards, rateLimit);
        when(auth.authenticate(any(), any(), any())).thenReturn(
                new ApiKeyModels.ApiPrincipal(SELLER, UUID.randomUUID(), List.of("payments_read", "payments_write")));
    }

    @Test
    void readsOnlyPaymentOwnedByAuthenticatedSeller() {
        UUID paymentId = UUID.randomUUID();
        var view = new ChargeCreationRepository.ChargeView(paymentId, "PIX", "PENDING", null, null,
                "000201...", Instant.parse("2026-11-10T15:30:00Z"));
        when(charges.viewOwned(SELLER, paymentId)).thenReturn(view);

        var response = controller.get("Bearer paysi_live_key", null, paymentId);

        assertThat(response.chargeId()).isEqualTo(paymentId);
        verify(auth).authenticate("Bearer paysi_live_key", null, ApiKeyModels.PAYMENTS_READ);
        verify(charges).viewOwned(SELLER, paymentId);
    }

    @Test
    void createsPixWithBuyerIpAndReturnsQrCodeAction() {
        UUID productId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID chargeId = UUID.randomUUID();
        Offer offer = mock(Offer.class);
        when(offer.productId()).thenReturn(productId);
        when(offers.findPublishedBySlug("oferta-pix")).thenReturn(Optional.of(offer));
        var order = new Order(orderId, UUID.randomUUID(), UUID.randomUUID(), null, "{\"name\":\"Ana\"}",
                10_000, 0, null, 10_000, OfferPaymentMethod.PIX, 1, OrderStatus.PENDING,
                "pedido-1", "hash", Instant.parse("2026-11-10T15:00:00Z"));
        when(orders.create(eq("oferta-pix"), eq("pedido-1"), any(CreateOrderCommand.class)))
                .thenReturn(new OrderResult(order, false));
        when(charges.start(eq(orderId), any(StartChargeCommand.class))).thenReturn(ChargeStartResult.pix(chargeId,
                new PixResult("provider-1", "000201...", Instant.parse("2026-11-10T15:30:00Z"), "pending", false)));
        var buyer = new PublicPaymentController.BuyerRequest("Ana", "ana@example.com", PersonType.PF,
                "12345678909", null, null, null, "11999999999");
        var request = new PublicPaymentController.CreatePaymentRequest("oferta-pix", buyer,
                OfferPaymentMethod.PIX, 1, null, "pedido-1", null, "device-1", " 203.0.113.42 ", "termos-v1",
                Instant.parse("2026-11-10T15:00:00Z"), null);
        HttpServletRequest servlet = mock(HttpServletRequest.class);
        when(servlet.getHeader("User-Agent")).thenReturn("Loja/1.0");

        var response = controller.create("Bearer paysi_live_key", null, "pedido-1", request, servlet);

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().payment().chargeId()).isEqualTo(chargeId);
        assertThat(response.getBody().nextAction()).isEqualTo("display_pix_qr_code");
        verify(rateLimit).checkOrderAttempt("203.0.113.42", "12345678909", "device-1");
        var orderCommand = org.mockito.ArgumentCaptor.forClass(CreateOrderCommand.class);
        verify(orders).create(eq("oferta-pix"), eq("pedido-1"), orderCommand.capture());
        assertThat(orderCommand.getValue().ip()).isEqualTo("203.0.113.42");
        var chargeCommand = org.mockito.ArgumentCaptor.forClass(StartChargeCommand.class);
        verify(charges).start(eq(orderId), chargeCommand.capture());
        assertThat(chargeCommand.getValue().ip()).isEqualTo("203.0.113.42");
    }

    @Test
    void refusesOfferFromAnotherSellerBeforeCreatingOrder() {
        UUID productId = UUID.randomUUID();
        Offer offer = mock(Offer.class);
        when(offer.productId()).thenReturn(productId);
        when(offers.findPublishedBySlug("oferta-alheia")).thenReturn(Optional.of(offer));
        when(products.get(SELLER, productId)).thenThrow(new NotFoundException("PRODUCT_NOT_FOUND", "Produto não encontrado"));
        var buyer = new PublicPaymentController.BuyerRequest("Ana", "ana@example.com", PersonType.PF,
                "12345678909", null, null, null, "11999999999");
        var request = new PublicPaymentController.CreatePaymentRequest("oferta-alheia", buyer,
                OfferPaymentMethod.PIX, 1, null, "pedido-1", null, "device-1", null, "termos-v1",
                Instant.parse("2026-11-10T15:00:00Z"), null);
        HttpServletRequest servlet = mock(HttpServletRequest.class);

        assertThatThrownBy(() -> controller.create("Bearer paysi_live_key", null, "pedido-1", request, servlet))
                .isInstanceOf(NotFoundException.class);
        verify(auth).authenticate("Bearer paysi_live_key", null, ApiKeyModels.PAYMENTS_WRITE);
        verifyNoInteractions(orders, charges, cards, rateLimit);
    }
}
