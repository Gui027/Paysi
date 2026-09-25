package com.paysi.affiliate.app;

import com.paysi.affiliate.domain.AffiliateProgram;
import com.paysi.affiliate.domain.AffiliationRecurrence;
import com.paysi.affiliate.port.AffiliateProgramRepository;
import com.paysi.catalog.product.app.ProductService;
import com.paysi.core.error.NotFoundException;
import com.paysi.core.error.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AffiliateProgramServiceTest {
    private static final UUID SELLER = UUID.randomUUID();
    private static final UUID PRODUCT = UUID.randomUUID();

    private AffiliateProgramRepository programs;
    private ProductService products;
    private AffiliateProgramService service;

    @BeforeEach
    void setUp() {
        programs = mock(AffiliateProgramRepository.class);
        products = mock(ProductService.class);
        service = new AffiliateProgramService(programs, products);
    }

    @Test
    void returnsDefaultsWhenProductHasNoProgramYet() {
        when(programs.find(PRODUCT)).thenReturn(Optional.empty());

        AffiliateProgram program = service.get(SELLER, PRODUCT);

        assertThat(program.autoApprove()).isFalse();
        assertThat(program.commissionBps()).isEqualTo(AffiliateProgram.DEFAULT_COMMISSION_BPS);
    }

    @Test
    void savesNormalizedProgram() {
        service.update(SELLER, PRODUCT, 2_500, AffiliationRecurrence.ALL_CYCLES, true, "  ajuda@loja.com ", "  ");

        var saved = ArgumentCaptor.forClass(AffiliateProgram.class);
        verify(programs).save(saved.capture());
        assertThat(saved.getValue().supportEmail()).isEqualTo("ajuda@loja.com");
        assertThat(saved.getValue().description()).isNull();
        assertThat(saved.getValue().autoApprove()).isTrue();
    }

    @Test
    void rejectsCommissionAboveFiftyPercentAndInvalidEmail() {
        assertThatThrownBy(() -> service.update(SELLER, PRODUCT, 5_001, AffiliationRecurrence.FIRST_CHARGE,
                false, null, null)).isInstanceOf(ValidationException.class).hasMessageContaining("50%");
        assertThatThrownBy(() -> service.update(SELLER, PRODUCT, 1_000, AffiliationRecurrence.FIRST_CHARGE,
                false, "nao-e-email", null)).isInstanceOf(ValidationException.class);
        verify(programs, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void doesNotTouchProgramOfProductOwnedBySomeoneElse() {
        when(products.get(SELLER, PRODUCT)).thenThrow(new NotFoundException("PRODUCT_NOT_FOUND", "Produto não encontrado"));

        assertThatThrownBy(() -> service.update(SELLER, PRODUCT, 1_000, AffiliationRecurrence.FIRST_CHARGE,
                false, null, null)).isInstanceOf(NotFoundException.class);
        verify(programs, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
