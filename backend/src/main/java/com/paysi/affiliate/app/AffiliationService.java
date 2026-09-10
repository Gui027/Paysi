package com.paysi.affiliate.app;

import com.paysi.affiliate.domain.Affiliation;
import com.paysi.affiliate.domain.AffiliationEndReason;
import com.paysi.affiliate.domain.AffiliationRecurrence;
import com.paysi.affiliate.port.AffiliateRepository;
import com.paysi.core.error.ForbiddenException;
import com.paysi.core.error.ConflictException;
import com.paysi.core.error.NotFoundException;
import com.paysi.core.error.ValidationException;
import com.paysi.identity.domain.KycStatus;
import com.paysi.identity.port.AccountRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class AffiliationService {
    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 50;

    private final AffiliateRepository affiliations;
    private final AccountRepository accounts;
    private final Clock clock;

    @Autowired
    public AffiliationService(AffiliateRepository affiliations, AccountRepository accounts) {
        this(affiliations, accounts, Clock.systemUTC());
    }

    AffiliationService(AffiliateRepository affiliations, AccountRepository accounts, Clock clock) {
        this.affiliations = affiliations;
        this.accounts = accounts;
        this.clock = clock;
    }

    @Transactional
    public Affiliation request(UUID affiliateId, UUID productId) {
        var account = accounts.findById(affiliateId).orElseThrow(AffiliationService::accountNotFound);
        if (account.kycStatus() != KycStatus.APPROVED) {
            throw new ForbiddenException("AFFILIATE_KYC_REQUIRED", "Conclua a verificação da conta para pedir afiliação");
        }
        var product = affiliations.findMarketplaceProduct(productId).orElseThrow(AffiliationService::productNotFound);
        if (product.sellerId().equals(affiliateId)) {
            throw new ConflictException("SELF_AFFILIATION_FORBIDDEN", "Não é permitido afiliar-se ao próprio produto", "productId");
        }
        return affiliations.request(UUID.randomUUID(), product.productId(), affiliateId, clock.instant());
    }

    @Transactional(readOnly = true)
    public AffiliationPage list(UUID accountId, AffiliationRole role, String rawCursor, Integer requestedLimit) {
        int limit = requestedLimit == null ? DEFAULT_LIMIT : Math.max(1, Math.min(requestedLimit, MAX_LIMIT));
        AffiliateCursor cursor = AffiliateCursorCodec.decode(rawCursor);
        List<Affiliation> rows = role == AffiliationRole.SELLER
                ? affiliations.listForSeller(accountId, cursor, limit + 1)
                : affiliations.listForAffiliate(accountId, cursor, limit + 1);
        boolean hasMore = rows.size() > limit;
        List<Affiliation> items = hasMore ? List.copyOf(rows.subList(0, limit)) : List.copyOf(rows);
        String next = hasMore ? AffiliateCursorCodec.encode(cursor(items.getLast())) : null;
        return new AffiliationPage(items, next);
    }

    @Transactional
    public Affiliation approve(UUID sellerId, UUID affiliationId, int commissionBps,
                               AffiliationRecurrence recurrence) {
        if (commissionBps < 0 || commissionBps > 5_000) {
            throw new ValidationException("AFFILIATION_COMMISSION_INVALID",
                    "A comissão deve estar entre 0 e 5000 pontos-base", "commissionBps");
        }
        if (recurrence == null) {
            throw new ValidationException("AFFILIATION_RECURRENCE_REQUIRED",
                    "Informe a recorrência da comissão", "recurrence");
        }
        Affiliation current = affiliations.find(affiliationId).orElseThrow(AffiliationService::notFound);
        if (!current.sellerId().equals(sellerId)) throw forbidden();
        return affiliations.approve(affiliationId, sellerId, commissionBps, recurrence, clock.instant())
                .orElseThrow(() -> new ValidationException("AFFILIATION_NOT_PENDING",
                        "Apenas uma solicitação pendente pode ser aprovada", "status"));
    }

    @Transactional
    public Affiliation end(UUID accountId, UUID affiliationId, AffiliationEndReason reason) {
        if (reason == null) {
            throw new ValidationException("AFFILIATION_END_REASON_REQUIRED",
                    "Informe o motivo do encerramento", "reason");
        }
        Affiliation current = affiliations.find(affiliationId).orElseThrow(AffiliationService::notFound);
        boolean seller = current.sellerId().equals(accountId);
        boolean affiliate = current.affiliateId().equals(accountId);
        if (!seller && !affiliate) throw forbidden();
        if ((reason == AffiliationEndReason.FRAUD || reason == AffiliationEndReason.BY_SELLER) && !seller
                || reason == AffiliationEndReason.BY_AFFILIATE && !affiliate) throw forbidden();
        return affiliations.end(affiliationId, accountId, reason, clock.instant())
                .orElseThrow(() -> new ValidationException("AFFILIATION_NOT_ACTIVE",
                        "A afiliação já foi encerrada", "status"));
    }

    private static AffiliateCursor cursor(Affiliation item) {
        return new AffiliateCursor(item.createdAt(), item.id());
    }

    private static NotFoundException productNotFound() {
        return new NotFoundException("MARKETPLACE_PRODUCT_NOT_FOUND", "Produto não disponível na vitrine");
    }

    private static NotFoundException accountNotFound() {
        return new NotFoundException("ACCOUNT_NOT_FOUND", "Conta não encontrada");
    }

    private static NotFoundException notFound() {
        return new NotFoundException("AFFILIATION_NOT_FOUND", "Afiliação não encontrada");
    }

    private static ForbiddenException forbidden() {
        return new ForbiddenException("AFFILIATION_FORBIDDEN", "Afiliação não pertence à conta autenticada");
    }
}
