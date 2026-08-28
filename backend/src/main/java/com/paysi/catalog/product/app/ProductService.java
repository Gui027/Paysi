package com.paysi.catalog.product.app;

import com.paysi.catalog.product.domain.Product;
import com.paysi.catalog.product.port.ProductRepository;
import com.paysi.core.error.ConflictException;
import com.paysi.core.error.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

@Service
public class ProductService {
    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;

    private final ProductRepository repository;
    private final Clock clock;

    public ProductService(ProductRepository repository) {
        this(repository, Clock.systemUTC());
    }

    ProductService(ProductRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public Product create(UUID sellerId, ProductCommand command) {
        Product product = Product.createDraft(UUID.randomUUID(), sellerId, command.name(),
                command.description(), command.segment(), command.chargeType(),
                command.affiliationEnabled(), clock.instant());
        repository.insert(product);
        return product;
    }

    @Transactional(readOnly = true)
    public ProductPage list(UUID sellerId, String encodedCursor, Integer requestedLimit) {
        int limit = requestedLimit == null ? DEFAULT_LIMIT : Math.min(Math.max(requestedLimit, 1), MAX_LIMIT);
        ProductCursor cursor = ProductCursorCodec.decode(encodedCursor);
        List<Product> rows = repository.listActiveOwned(sellerId, cursor, limit + 1);
        boolean hasMore = rows.size() > limit;
        List<Product> items = List.copyOf(hasMore ? rows.subList(0, limit) : rows);
        String nextCursor = hasMore ? ProductCursorCodec.encode(cursorOf(items.getLast())) : null;
        return new ProductPage(items, nextCursor);
    }

    @Transactional(readOnly = true)
    public Product get(UUID sellerId, UUID productId) {
        return requireOwned(sellerId, productId);
    }

    @Transactional
    public Product update(UUID sellerId, UUID productId, ProductCommand command) {
        Product current = requireOwned(sellerId, productId);
        if (repository.hasOffers(productId)
                && (current.segment() != command.segment() || current.chargeType() != command.chargeType())) {
            throw new ConflictException("PRODUCT_CONTRACT_IMMUTABLE",
                    "Segmento e tipo de cobrança não podem mudar após a criação de uma oferta", null);
        }
        Product changed = current.update(command.name(), command.description(), command.segment(),
                command.chargeType(), command.affiliationEnabled());
        repository.update(changed);
        return changed;
    }

    @Transactional
    public void archive(UUID sellerId, UUID productId) {
        requireOwned(sellerId, productId);
        if (!repository.archive(sellerId, productId, clock.instant())) throw notFound();
    }

    private Product requireOwned(UUID sellerId, UUID productId) {
        return repository.findActiveOwned(sellerId, productId).orElseThrow(ProductService::notFound);
    }

    private static ProductCursor cursorOf(Product product) {
        return new ProductCursor(product.createdAt(), product.id());
    }

    private static NotFoundException notFound() {
        return new NotFoundException("PRODUCT_NOT_FOUND", "Produto não encontrado");
    }
}
