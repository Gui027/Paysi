package com.paysi.affiliate.app;

import com.paysi.affiliate.domain.Affiliation;

import java.util.List;

public record AffiliationPage(List<Affiliation> items, String nextCursor) {
}
