package com.paysi.checkout.order.web;

import com.paysi.catalog.offer.domain.OfferPaymentMethod;
import com.paysi.checkout.order.app.CreateOrderCommand;
import com.paysi.checkout.order.app.CreateOrderService;
import com.paysi.checkout.order.app.OrderResult;
import com.paysi.checkout.order.domain.Order;
import com.paysi.checkout.order.domain.OrderStatus;
import com.paysi.core.error.ConflictException;
import com.paysi.core.error.ValidationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CheckoutOrderController.class)
class CheckoutOrderControllerTest {
    private static final String SLUG = "curso-de-teste";
    private static final String KEY = "chave-do-navegador-1";
    private static final Instant NOW = Instant.parse("2026-09-16T12:00:00Z");

    private static final String BODY = """
            {"buyer":{"name":"Ana","email":"ana@example.com","personType":"PF",
              "taxId":"529.982.247-25"},
             "method":"PIX","installments":1,"termsHash":"hash-dos-termos"}
            """;

    @Autowired private MockMvc mvc;
    @MockitoBean private CreateOrderService orders;

    @Test
    void criaPedidoComDuzentosEUm() throws Exception {
        when(orders.create(eq(SLUG), eq(KEY), any(CreateOrderCommand.class)))
                .thenReturn(new OrderResult(order(), false));

        mvc.perform(post("/v1/checkout/{slug}/orders", SLUG)
                        .header("Idempotency-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.paidCents").value(17_700))
                .andExpect(jsonPath("$.grossCents").value(17_700));
    }

    @Test
    void repeticaoDevolveDuzentosComOMesmoPedido() throws Exception {
        Order existing = order();
        when(orders.create(eq(SLUG), eq(KEY), any(CreateOrderCommand.class)))
                .thenReturn(new OrderResult(existing, true));

        mvc.perform(post("/v1/checkout/{slug}/orders", SLUG)
                        .header("Idempotency-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(existing.id().toString()));
    }

    @Test
    void semCabecalhoDeIdempotenciaDaQuatrocentos() throws Exception {
        when(orders.create(eq(SLUG), eq(null), any(CreateOrderCommand.class)))
                .thenThrow(new ValidationException("IDEMPOTENCY_KEY_REQUIRED",
                        "Idempotency-Key é obrigatório", "Idempotency-Key"));

        mvc.perform(post("/v1/checkout/{slug}/orders", SLUG)
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"))
                .andExpect(jsonPath("$.field").value("Idempotency-Key"));
    }

    @Test
    void chaveReutilizadaComOutroCorpoDaQuatrocentosENove() throws Exception {
        when(orders.create(eq(SLUG), eq(KEY), any(CreateOrderCommand.class)))
                .thenThrow(new ConflictException("IDEMPOTENCY_KEY_REUSED",
                        "A chave já foi usada com outro conteúdo", "Idempotency-Key"));

        mvc.perform(post("/v1/checkout/{slug}/orders", SLUG)
                        .header("Idempotency-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
    }

    @Test
    void corpoQueTentaMandarValorEhRecusadoNaoIgnorado() throws Exception {
        String comValor = """
                {"buyer":{"name":"Ana","email":"ana@example.com","personType":"PF",
                  "taxId":"529.982.247-25"},
                 "method":"PIX","installments":1,"termsHash":"hash-dos-termos",
                 "amountCents":100}
                """;

        mvc.perform(post("/v1/checkout/{slug}/orders", SLUG)
                        .header("Idempotency-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(comValor))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNKNOWN_FIELD"))
                .andExpect(jsonPath("$.field").value("amountCents"));
        verify(orders, never()).create(any(), any(), any());
    }

    @Test
    void compradorSemCamposObrigatoriosNaoChegaAoServico() throws Exception {
        String semEmail = """
                {"buyer":{"name":"Ana","personType":"PF","taxId":"529.982.247-25"},
                 "method":"PIX","termsHash":"hash-dos-termos"}
                """;

        mvc.perform(post("/v1/checkout/{slug}/orders", SLUG)
                        .header("Idempotency-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(semEmail))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verify(orders, never()).create(any(), any(), any());
    }

    private static Order order() {
        return new Order(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null,
                "{\"name\":\"Ana\"}", 17_700, 0, null, 17_700, OfferPaymentMethod.PIX, 1,
                OrderStatus.PENDING, KEY, "impressao", NOW);
    }
}
