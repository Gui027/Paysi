package com.paysi.affiliate.web.dto;

import com.paysi.affiliate.app.AffiliationPage;

import java.util.List;

public record AffiliationPageResponse(List<AffiliationResponse> items, String nextCursor) {
    public static AffiliationPageResponse from(AffiliationPage page) {
        return new AffiliationPageResponse(page.items().stream().map(AffiliationResponse::from).toList(),
                page.nextCursor());
    }
}
