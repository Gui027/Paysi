package com.paysi.catalog.product.web;

import com.paysi.catalog.product.app.ProductService;
import com.paysi.catalog.product.web.dto.ProductPageResponse;
import com.paysi.catalog.product.web.dto.ProductRequest;
import com.paysi.catalog.product.web.dto.ProductResponse;
import com.paysi.identity.session.app.SessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/v1/products")
@Tag(name = "Produtos")
public class ProductController {
    private static final String COOKIE_NAME = "paysi_session";

    private final ProductService products;
    private final SessionService sessions;

    public ProductController(ProductService products, SessionService sessions) {
        this.products = products;
        this.sessions = sessions;
    }

    @PostMapping
    @Operation(summary = "Criar produto em rascunho")
    public ResponseEntity<ProductResponse> create(
            @CookieValue(name = COOKIE_NAME, required = false) String token,
            @Valid @RequestBody ProductRequest request) {
        UUID sellerId = accountId(token);
        var created = products.create(sellerId, request.toCommand());
        return ResponseEntity.created(URI.create("/v1/products/" + created.id()))
                .body(ProductResponse.from(created));
    }

    @GetMapping
    @Operation(summary = "Listar produtos por cursor")
    public ProductPageResponse list(
            @CookieValue(name = COOKIE_NAME, required = false) String token,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {
        return ProductPageResponse.from(products.list(accountId(token), cursor, limit));
    }

    @GetMapping("/{productId}")
    @Operation(summary = "Detalhar produto")
    public ProductResponse detail(
            @CookieValue(name = COOKIE_NAME, required = false) String token,
            @PathVariable UUID productId) {
        return ProductResponse.from(products.get(accountId(token), productId));
    }

    @PutMapping("/{productId}")
    @Operation(summary = "Editar produto")
    public ProductResponse update(
            @CookieValue(name = COOKIE_NAME, required = false) String token,
            @PathVariable UUID productId,
            @Valid @RequestBody ProductRequest request) {
        return ProductResponse.from(products.update(accountId(token), productId, request.toCommand()));
    }

    @DeleteMapping("/{productId}")
    @Operation(summary = "Arquivar produto")
    public ResponseEntity<Void> archive(
            @CookieValue(name = COOKIE_NAME, required = false) String token,
            @PathVariable UUID productId) {
        products.archive(accountId(token), productId);
        return ResponseEntity.noContent().build();
    }

    private UUID accountId(String token) {
        return sessions.authenticate(token).session().accountId();
    }
}
