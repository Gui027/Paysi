package com.paysi.affiliate.app;

import com.paysi.affiliate.domain.Affiliation;
import com.paysi.affiliate.domain.AffiliationEndReason;
import com.paysi.affiliate.domain.AffiliationRecurrence;
import com.paysi.affiliate.domain.AffiliationStatus;
import com.paysi.affiliate.port.AffiliateRepository;
import com.paysi.catalog.product.domain.ChargeType;
import com.paysi.catalog.product.domain.Segment;
import com.paysi.core.error.ConflictException;
import com.paysi.core.error.ForbiddenException;
import com.paysi.identity.domain.Account;
import com.paysi.identity.domain.AccountStatus;
import com.paysi.identity.domain.KycStatus;
import com.paysi.identity.domain.PayoutDelay;
import com.paysi.identity.domain.PersonType;
import com.paysi.identity.domain.TaxId;
import com.paysi.identity.port.AccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AffiliationServiceTest {
    private static final UUID AFFILIATE = UUID.randomUUID();
    private static final UUID SELLER = UUID.randomUUID();
    private static final UUID OTHER = UUID.randomUUID();
    private static final UUID PRODUCT = UUID.randomUUID();
    private static final UUID AFFILIATION = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-09-10T12:00:00Z");

    private AffiliateRepository repository;
    private AccountRepository accounts;
    private AffiliationService service;

    @BeforeEach
    void setUp() {
        repository = mock(AffiliateRepository.class);
        accounts = mock(AccountRepository.class);
        service = new AffiliationService(repository, accounts, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void verifiedSellerCanAlsoRequestAffiliation() {
        when(accounts.findById(AFFILIATE)).thenReturn(Optional.of(account(AFFILIATE, KycStatus.APPROVED)));
        when(repository.findMarketplaceProduct(PRODUCT)).thenReturn(Optional.of(product(SELLER)));
        when(repository.request(any(), eq(PRODUCT), eq(AFFILIATE), eq(NOW)))
                .thenAnswer(call -> affiliation(call.getArgument(0), AffiliationStatus.PENDING));

        Affiliation created = service.request(AFFILIATE, PRODUCT);

        assertThat(created.status()).isEqualTo(AffiliationStatus.PENDING);
        verify(repository).request(any(UUID.class), eq(PRODUCT), eq(AFFILIATE), eq(NOW));
    }

    @Test
    void requiresApprovedKycAndRejectsSelfAffiliation() {
        when(accounts.findById(AFFILIATE)).thenReturn(Optional.of(account(AFFILIATE, KycStatus.PENDING)));
        assertThatThrownBy(() -> service.request(AFFILIATE, PRODUCT))
                .isInstanceOfSatisfying(ForbiddenException.class,
                        error -> assertThat(error.code()).isEqualTo("AFFILIATE_KYC_REQUIRED"));

        when(accounts.findById(AFFILIATE)).thenReturn(Optional.of(account(AFFILIATE, KycStatus.APPROVED)));
        when(repository.findMarketplaceProduct(PRODUCT)).thenReturn(Optional.of(product(AFFILIATE)));
        assertThatThrownBy(() -> service.request(AFFILIATE, PRODUCT))
                .isInstanceOfSatisfying(ConflictException.class,
                        error -> assertThat(error.code()).isEqualTo("SELF_AFFILIATION_FORBIDDEN"));
    }

    @Test
    void onlyOwnerSellerApprovesAndCommissionIsBounded() {
        Affiliation pending = affiliation(AFFILIATION, AffiliationStatus.PENDING);
        when(repository.find(AFFILIATION)).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> service.approve(OTHER, AFFILIATION, 1_000,
                AffiliationRecurrence.ALL_CYCLES)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service.approve(SELLER, AFFILIATION, 5_001,
                AffiliationRecurrence.FIRST_CHARGE)).hasMessageContaining("5000");

        when(repository.approve(AFFILIATION, SELLER, 1_000,
                AffiliationRecurrence.ALL_CYCLES, NOW))
                .thenReturn(Optional.of(affiliation(AFFILIATION, AffiliationStatus.APPROVED)));
        assertThat(service.approve(SELLER, AFFILIATION, 1_000, AffiliationRecurrence.ALL_CYCLES).status())
                .isEqualTo(AffiliationStatus.APPROVED);
    }

    @Test
    void fraudEndingIsRestrictedToSeller() {
        when(repository.find(AFFILIATION)).thenReturn(Optional.of(affiliation(AFFILIATION,
                AffiliationStatus.APPROVED)));

        assertThatThrownBy(() -> service.end(AFFILIATE, AFFILIATION, AffiliationEndReason.FRAUD))
                .isInstanceOf(ForbiddenException.class);
        when(repository.end(AFFILIATION, SELLER, AffiliationEndReason.FRAUD, NOW))
                .thenReturn(Optional.of(affiliation(AFFILIATION, AffiliationStatus.FRAUD_ENDED)));
        assertThat(service.end(SELLER, AFFILIATION, AffiliationEndReason.FRAUD).status())
                .isEqualTo(AffiliationStatus.FRAUD_ENDED);
    }

    private static MarketplaceItem product(UUID sellerId) {
        return new MarketplaceItem(PRODUCT, sellerId, "Produto", "Descrição", "Vendedor",
                Segment.DIGITAL, ChargeType.ONE_TIME, 2_000, 1_500, 7, 32, 60, NOW);
    }

    private static Affiliation affiliation(UUID id, AffiliationStatus status) {
        return new Affiliation(id, PRODUCT, "Produto", SELLER, "Vendedor", AFFILIATE, "Afiliado",
                status == AffiliationStatus.PENDING ? 0 : 1_000, AffiliationRecurrence.ALL_CYCLES,
                status, status == AffiliationStatus.FRAUD_ENDED ? AffiliationEndReason.FRAUD : null,
                status == AffiliationStatus.PENDING ? null : NOW, null, NOW);
    }

    private static Account account(UUID id, KycStatus kyc) {
        return Account.reconstitute(id, id + "@example.com", "hash", "Conta", PersonType.PF,
                new TaxId("52998224725"), kyc, PayoutDelay.D32, 0, AccountStatus.ACTIVE, NOW);
    }
}
