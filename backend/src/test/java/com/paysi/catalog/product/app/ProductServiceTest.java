package com.paysi.catalog.product.app;

import com.paysi.catalog.product.domain.ChargeType;
import com.paysi.catalog.product.domain.Product;
import com.paysi.catalog.product.domain.ProductStatus;
import com.paysi.catalog.product.domain.Segment;
import com.paysi.catalog.product.port.ProductRepository;
import com.paysi.core.error.ConflictException;
import com.paysi.core.error.NotFoundException;
import com.paysi.core.error.ValidationException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductServiceTest {
    private static final UUID SELLER = UUID.randomUUID();
    private static final UUID OTHER_SELLER = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-08-28T12:00:00Z");

    @Test
    void createsNormalizedDraftWithoutOffer() {
        var repository = new InMemoryProducts();
        var service = service(repository);

        Product created = service.create(SELLER,
                command("  CRM Pro  ", "  Automação comercial  ", Segment.SAAS,
                        ChargeType.SUBSCRIPTION, true));

        assertThat(created.name()).isEqualTo("CRM Pro");
        assertThat(created.description()).isEqualTo("Automação comercial");
        assertThat(created.status()).isEqualTo(ProductStatus.DRAFT);
        assertThat(created.createdAt()).isEqualTo(NOW);
        assertThat(created.sellerId()).isEqualTo(SELLER);
        assertThat(repository.products).singleElement().isEqualTo(created);
        assertThat(repository.offerProducts).isEmpty();
    }

    @Test
    void validatesNormalizedFieldLimits() {
        var service = service(new InMemoryProducts());

        assertThatThrownBy(() -> service.create(SELLER,
                command(" ", null, Segment.DIGITAL, ChargeType.ONE_TIME, false)))
                .isInstanceOfSatisfying(ValidationException.class,
                        error -> assertThat(error.field()).isEqualTo("name"));
        assertThatThrownBy(() -> service.create(SELLER,
                command("x".repeat(121), null, Segment.DIGITAL, ChargeType.ONE_TIME, false)))
                .isInstanceOfSatisfying(ValidationException.class,
                        error -> assertThat(error.code()).isEqualTo("PRODUCT_NAME_TOO_LONG"));
        assertThatThrownBy(() -> service.create(SELLER,
                command("Produto", "x".repeat(2001), Segment.DIGITAL, ChargeType.ONE_TIME, false)))
                .isInstanceOfSatisfying(ValidationException.class,
                        error -> assertThat(error.field()).isEqualTo("description"));
    }

    @Test
    void paginatesByCreatedAtAndIdWithoutRepeatingItems() {
        var repository = new InMemoryProducts();
        for (int index = 0; index < 5; index++) {
            repository.products.add(product(SELLER, "Produto " + index, NOW.minusSeconds(index)));
        }
        var service = service(repository);

        ProductPage first = service.list(SELLER, null, 2);
        ProductPage second = service.list(SELLER, first.nextCursor(), 2);
        ProductPage third = service.list(SELLER, second.nextCursor(), 2);

        assertThat(first.items()).extracting(Product::name).containsExactly("Produto 0", "Produto 1");
        assertThat(second.items()).extracting(Product::name).containsExactly("Produto 2", "Produto 3");
        assertThat(third.items()).extracting(Product::name).containsExactly("Produto 4");
        assertThat(third.nextCursor()).isNull();
    }

    @Test
    void rejectsInvalidCursor() {
        assertThatThrownBy(() -> service(new InMemoryProducts()).list(SELLER, "not-base64", 20))
                .isInstanceOfSatisfying(ValidationException.class,
                        error -> assertThat(error.code()).isEqualTo("PRODUCT_CURSOR_INVALID"));
    }

    @Test
    void hidesProductsOwnedByAnotherSeller() {
        var repository = new InMemoryProducts();
        Product foreign = product(OTHER_SELLER, "Alheio", NOW);
        repository.products.add(foreign);

        assertThatThrownBy(() -> service(repository).get(SELLER, foreign.id()))
                .isInstanceOfSatisfying(NotFoundException.class,
                        error -> assertThat(error.code()).isEqualTo("PRODUCT_NOT_FOUND"));
    }

    @Test
    void keepsContractImmutableAfterOfferButAllowsOtherFields() {
        var repository = new InMemoryProducts();
        Product stored = product(SELLER, "Original", NOW);
        repository.products.add(stored);
        repository.offerProducts.add(stored.id());
        var service = service(repository);

        assertThatThrownBy(() -> service.update(SELLER, stored.id(),
                command("Novo", null, Segment.SAAS, ChargeType.SUBSCRIPTION, true)))
                .isInstanceOfSatisfying(ConflictException.class,
                        error -> assertThat(error.code()).isEqualTo("PRODUCT_CONTRACT_IMMUTABLE"));

        Product updated = service.update(SELLER, stored.id(),
                command("Novo", "Descrição", Segment.DIGITAL, ChargeType.ONE_TIME, true));
        assertThat(updated.name()).isEqualTo("Novo");
        assertThat(updated.affiliationEnabled()).isTrue();
    }

    @Test
    void archivesLogicallyAndThenReturnsNotFound() {
        var repository = new InMemoryProducts();
        Product stored = product(SELLER, "Produto", NOW);
        repository.products.add(stored);
        var service = service(repository);

        service.archive(SELLER, stored.id());

        assertThatThrownBy(() -> service.get(SELLER, stored.id()))
                .isInstanceOf(NotFoundException.class);
        assertThat(service.list(SELLER, null, 20).items()).isEmpty();
    }

    private static ProductService service(InMemoryProducts repository) {
        return new ProductService(repository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static ProductCommand command(String name, String description, Segment segment,
                                          ChargeType chargeType, boolean affiliationEnabled) {
        return new ProductCommand(name, description, segment, chargeType, affiliationEnabled);
    }

    private static Product product(UUID sellerId, String name, Instant createdAt) {
        return Product.createDraft(UUID.randomUUID(), sellerId, name, null, Segment.DIGITAL,
                ChargeType.ONE_TIME, false, createdAt);
    }

    private static final class InMemoryProducts implements ProductRepository {
        private final List<Product> products = new ArrayList<>();
        private final List<UUID> offerProducts = new ArrayList<>();

        @Override
        public void insert(Product product) {
            products.add(product);
        }

        @Override
        public Optional<Product> findActiveOwned(UUID sellerId, UUID productId) {
            return products.stream().filter(product -> product.id().equals(productId))
                    .filter(product -> product.sellerId().equals(sellerId))
                    .filter(product -> product.archivedAt() == null).findFirst();
        }

        @Override
        public List<Product> listActiveOwned(UUID sellerId, ProductCursor cursor, int limit) {
            Comparator<Product> order = Comparator.comparing(Product::createdAt)
                    .thenComparing(Product::id).reversed();
            return products.stream().filter(product -> product.sellerId().equals(sellerId))
                    .filter(product -> product.archivedAt() == null)
                    .filter(product -> cursor == null || product.createdAt().isBefore(cursor.createdAt())
                            || (product.createdAt().equals(cursor.createdAt())
                            && product.id().compareTo(cursor.id()) < 0))
                    .sorted(order).limit(limit).toList();
        }

        @Override
        public boolean hasOffers(UUID productId) {
            return offerProducts.contains(productId);
        }

        @Override
        public void update(Product product) {
            products.replaceAll(current -> current.id().equals(product.id()) ? product : current);
        }

        @Override
        public boolean archive(UUID sellerId, UUID productId, Instant archivedAt) {
            for (int index = 0; index < products.size(); index++) {
                Product current = products.get(index);
                if (current.id().equals(productId) && current.sellerId().equals(sellerId)
                        && current.archivedAt() == null) {
                    products.set(index, new Product(current.id(), current.sellerId(), current.name(),
                            current.description(), current.segment(), current.chargeType(),
                            current.affiliationEnabled(), current.status(), archivedAt, current.createdAt()));
                    return true;
                }
            }
            return false;
        }
    }
}
