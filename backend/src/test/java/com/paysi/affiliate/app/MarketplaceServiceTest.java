package com.paysi.affiliate.app;

import com.paysi.affiliate.port.AffiliateRepository;
import com.paysi.catalog.product.domain.ChargeType;
import com.paysi.catalog.product.domain.Segment;
import com.paysi.core.error.ValidationException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MarketplaceServiceTest {
    @Test
    void limitsPageAndProducesOpaqueCursor() {
        AffiliateRepository repository = mock(AffiliateRepository.class);
        MarketplaceService service = new MarketplaceService(repository);
        Instant now = Instant.parse("2026-09-10T12:00:00Z");
        when(repository.listMarketplace(null, 2)).thenReturn(List.of(item(now), item(now.minusSeconds(1))));

        MarketplacePage first = service.list(null, 1);
        assertThat(first.items()).hasSize(1);
        assertThat(first.nextCursor()).isNotBlank().doesNotContain("|");

        when(repository.listMarketplace(any(AffiliateCursor.class), org.mockito.ArgumentMatchers.eq(2)))
                .thenReturn(List.of());
        assertThat(service.list(first.nextCursor(), 1).items()).isEmpty();
        verify(repository).listMarketplace(any(AffiliateCursor.class), org.mockito.ArgumentMatchers.eq(2));
    }

    @Test
    void rejectsMalformedCursor() {
        MarketplaceService service = new MarketplaceService(mock(AffiliateRepository.class));
        assertThatThrownBy(() -> service.list("nao-e-cursor", 20))
                .isInstanceOfSatisfying(ValidationException.class,
                        error -> assertThat(error.code()).isEqualTo("AFFILIATE_CURSOR_INVALID"));
    }

    private static MarketplaceItem item(Instant createdAt) {
        return new MarketplaceItem(UUID.randomUUID(), UUID.randomUUID(), "Produto", null, "Vendedor",
                Segment.SAAS, ChargeType.SUBSCRIPTION, 10_000, 1_500, 7, 32, 60, createdAt);
    }
}
