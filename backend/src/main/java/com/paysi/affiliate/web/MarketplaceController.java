package com.paysi.affiliate.web;

import com.paysi.affiliate.app.MarketplaceService;
import com.paysi.affiliate.web.dto.MarketplacePageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/marketplace")
@Tag(name = "Afiliações")
public class MarketplaceController {
    private final MarketplaceService marketplace;

    public MarketplaceController(MarketplaceService marketplace) {
        this.marketplace = marketplace;
    }

    @GetMapping
    @Operation(summary = "Listar produtos publicados com afiliação habilitada")
    public MarketplacePageResponse list(@RequestParam(required = false) String cursor,
                                        @RequestParam(required = false) Integer limit) {
        return MarketplacePageResponse.from(marketplace.list(cursor, limit));
    }
}
