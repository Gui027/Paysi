package com.paysi.catalog.offer.web;

import com.paysi.catalog.offer.app.OfferService;
import com.paysi.catalog.offer.app.OfferPublicationService;
import com.paysi.catalog.offer.web.dto.OfferPublicationResponse;
import com.paysi.catalog.offer.web.dto.OfferRequest;
import com.paysi.catalog.offer.web.dto.OfferResponse;
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
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1")
@Tag(name = "Ofertas")
public class OfferController {
    private static final String COOKIE_NAME = "paysi_session";

    private final OfferService offers;
    private final OfferPublicationService publications;
    private final SessionService sessions;

    public OfferController(OfferService offers, OfferPublicationService publications, SessionService sessions) {
        this.offers = offers;
        this.publications = publications;
        this.sessions = sessions;
    }

    @PostMapping("/products/{productId}/offers")
    @Operation(summary = "Criar oferta em rascunho")
    public ResponseEntity<OfferResponse> create(
            @CookieValue(name = COOKIE_NAME, required = false) String token,
            @PathVariable UUID productId, @Valid @RequestBody OfferRequest request) {
        var created = offers.create(accountId(token), productId, request.toValues());
        return ResponseEntity.created(URI.create("/v1/offers/" + created.offer().id()))
                .body(OfferResponse.from(created));
    }

    @GetMapping("/products/{productId}/offers")
    @Operation(summary = "Listar ofertas do produto")
    public List<OfferResponse> list(
            @CookieValue(name = COOKIE_NAME, required = false) String token,
            @PathVariable UUID productId) {
        return offers.list(accountId(token), productId).stream().map(OfferResponse::from).toList();
    }

    @GetMapping("/offers/{offerId}")
    @Operation(summary = "Detalhar oferta")
    public OfferResponse detail(
            @CookieValue(name = COOKIE_NAME, required = false) String token,
            @PathVariable UUID offerId) {
        return OfferResponse.from(offers.get(accountId(token), offerId));
    }

    @PostMapping("/offers/{offerId}/publish")
    @Operation(summary = "Publicar oferta")
    public ResponseEntity<OfferPublicationResponse> publish(
            @CookieValue(name = COOKIE_NAME, required = false) String token,
            @PathVariable UUID offerId) {
        OfferPublicationResponse response = OfferPublicationResponse.from(
                publications.publish(accountId(token), offerId));
        return response.published() ? ResponseEntity.ok(response) : ResponseEntity.accepted().body(response);
    }

    @PutMapping("/offers/{offerId}")
    @Operation(summary = "Editar oferta")
    public OfferResponse update(
            @CookieValue(name = COOKIE_NAME, required = false) String token,
            @PathVariable UUID offerId, @Valid @RequestBody OfferRequest request) {
        return OfferResponse.from(offers.update(accountId(token), offerId, request.toValues()));
    }

    @DeleteMapping("/offers/{offerId}")
    @Operation(summary = "Arquivar oferta")
    public ResponseEntity<Void> archive(
            @CookieValue(name = COOKIE_NAME, required = false) String token,
            @PathVariable UUID offerId) {
        offers.archive(accountId(token), offerId);
        return ResponseEntity.noContent().build();
    }

    private UUID accountId(String token) {
        return sessions.authenticate(token).session().accountId();
    }
}
