package com.paysi.catalog.product.web;

import com.paysi.catalog.product.app.ProductCommand;
import com.paysi.catalog.product.app.ProductPage;
import com.paysi.catalog.product.app.ProductService;
import com.paysi.catalog.product.domain.ChargeType;
import com.paysi.catalog.product.domain.Product;
import com.paysi.catalog.product.domain.Segment;
import com.paysi.core.error.NotFoundException;
import com.paysi.identity.domain.InitialMode;
import com.paysi.identity.session.app.AuthenticatedSession;
import com.paysi.identity.session.app.SessionService;
import com.paysi.identity.session.app.SessionView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProductController.class)
class ProductControllerTest {
    private static final UUID SELLER = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-08-28T12:00:00Z");

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ProductService products;

    @MockitoBean
    private SessionService sessions;

    @BeforeEach
    void authenticate() {
        when(sessions.authenticate("cookie")).thenReturn(new AuthenticatedSession("cookie",
                new SessionView(SELLER, InitialMode.SELLER, NOW, NOW.plusSeconds(3600))));
    }

    @Test
    void createsDraftUsingSellerFromSessionWithoutInternalFields() throws Exception {
        Product created = product("CRM Pro");
        when(products.create(eq(SELLER), any(ProductCommand.class))).thenReturn(created);

        mvc.perform(post("/v1/products").cookie(new jakarta.servlet.http.Cookie("paysi_session", "cookie"))
                        .contentType(MediaType.APPLICATION_JSON).content(validBody()))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/v1/products/" + created.id()))
                .andExpect(jsonPath("$.id").value(created.id().toString()))
                .andExpect(jsonPath("$.name").value("CRM Pro"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.sellerId").doesNotExist())
                .andExpect(jsonPath("$.archivedAt").doesNotExist())
                .andExpect(jsonPath("$.slug").doesNotExist());

        verify(products).create(eq(SELLER), any(ProductCommand.class));
    }

    @Test
    void listsDetailsUpdatesAndArchivesUsingSessionAccount() throws Exception {
        Product stored = product("Produto");
        when(products.list(SELLER, null, 20)).thenReturn(new ProductPage(List.of(stored), null));
        when(products.get(SELLER, stored.id())).thenReturn(stored);
        when(products.update(eq(SELLER), eq(stored.id()), any())).thenReturn(stored);

        mvc.perform(get("/v1/products").cookie(cookie()).param("limit", "20"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value(stored.id().toString()));
        mvc.perform(get("/v1/products/{id}", stored.id()).cookie(cookie()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(stored.id().toString()));
        mvc.perform(put("/v1/products/{id}", stored.id()).cookie(cookie())
                        .contentType(MediaType.APPLICATION_JSON).content(validBody()))
                .andExpect(status().isOk());
        mvc.perform(delete("/v1/products/{id}", stored.id()).cookie(cookie()))
                .andExpect(status().isNoContent());

        verify(products).archive(SELLER, stored.id());
    }

    @Test
    void rejectsReadOnlyStatusAndUnknownEnumWithStableErrors() throws Exception {
        mvc.perform(post("/v1/products").cookie(cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content(validBody().replace("\"affiliationEnabled\": true",
                                "\"affiliationEnabled\": true, \"status\": \"ACTIVE\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("status"));

        mvc.perform(post("/v1/products").cookie(cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content(validBody().replace("SAAS", "INVALID")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ENUM"))
                .andExpect(jsonPath("$.field").value("segment"));

        verifyNoInteractions(products);
    }

    @Test
    void returnsSame404ForUnavailableProduct() throws Exception {
        UUID id = UUID.randomUUID();
        when(products.get(SELLER, id)).thenThrow(new NotFoundException("PRODUCT_NOT_FOUND",
                "Produto não encontrado"));

        mvc.perform(get("/v1/products/{id}", id).cookie(cookie()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    void requiresAuthenticatedSession() throws Exception {
        when(sessions.authenticate(null)).thenThrow(new com.paysi.core.error.UnauthorizedException(
                "SESSION_INVALID", "Sessão ausente ou expirada"));

        mvc.perform(get("/v1/products"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_INVALID"));
    }

    private static jakarta.servlet.http.Cookie cookie() {
        return new jakarta.servlet.http.Cookie("paysi_session", "cookie");
    }

    private static String validBody() {
        return """
                {
                  "name": "CRM Pro",
                  "description": "Automação comercial",
                  "segment": "SAAS",
                  "chargeType": "SUBSCRIPTION",
                  "affiliationEnabled": true
                }
                """;
    }

    private static Product product(String name) {
        return Product.createDraft(UUID.randomUUID(), SELLER, name, "Descrição", Segment.SAAS,
                ChargeType.SUBSCRIPTION, true, NOW);
    }
}
