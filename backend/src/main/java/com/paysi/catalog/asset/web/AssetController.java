package com.paysi.catalog.asset.web;

import com.paysi.catalog.asset.app.AssetDownload;
import com.paysi.catalog.asset.app.AssetService;
import com.paysi.catalog.asset.domain.AssetKind;
import com.paysi.catalog.asset.web.dto.AssetResponse;
import com.paysi.identity.session.app.SessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.UUID;

@RestController
@RequestMapping("/v1/assets")
@Tag(name = "Ativos do checkout")
public class AssetController {
    private static final String COOKIE_NAME = "paysi_session";

    private final AssetService assets;
    private final SessionService sessions;

    public AssetController(AssetService assets, SessionService sessions) {
        this.assets = assets;
        this.sessions = sessions;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Enviar imagem hospedada pela Paysi")
    public ResponseEntity<AssetResponse> upload(
            @CookieValue(name = COOKIE_NAME, required = false) String token,
            @RequestParam AssetKind kind, @RequestPart MultipartFile file) throws IOException {
        AssetResponse response = AssetResponse.from(assets.upload(accountId(token), kind, file.getBytes()));
        return ResponseEntity.created(URI.create("/v1/assets/" + response.id())).body(response);
    }

    @GetMapping("/{assetId}/content")
    @Operation(summary = "Carregar conteúdo público de um ativo")
    public ResponseEntity<byte[]> content(@PathVariable UUID assetId) {
        AssetDownload download = assets.download(assetId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(download.contentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofHours(24)).cachePublic())
                .header("X-Content-Type-Options", "nosniff")
                .body(download.content());
    }

    @DeleteMapping("/{assetId}")
    @Operation(summary = "Remover ativo próprio")
    public ResponseEntity<Void> remove(
            @CookieValue(name = COOKIE_NAME, required = false) String token,
            @PathVariable UUID assetId) {
        assets.remove(accountId(token), assetId);
        return ResponseEntity.noContent().build();
    }

    private UUID accountId(String token) {
        return sessions.authenticate(token).session().accountId();
    }
}
