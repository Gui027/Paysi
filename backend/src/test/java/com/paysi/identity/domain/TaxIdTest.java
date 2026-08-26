package com.paysi.identity.domain;

import com.paysi.core.error.ValidationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TaxIdTest {

    @Test
    void normalizesAndValidatesCpf() {
        assertThat(TaxId.of("529.982.247-25", PersonType.PF).digits()).isEqualTo("52998224725");
    }

    @Test
    void normalizesAndValidatesCnpj() {
        assertThat(TaxId.of("04.252.011/0001-10", PersonType.PJ).digits()).isEqualTo("04252011000110");
    }

    @Test
    void rejectsInvalidDocumentForSelectedPersonType() {
        assertThatThrownBy(() -> TaxId.of("529.982.247-24", PersonType.PF))
                .isInstanceOfSatisfying(ValidationException.class, error -> {
                    assertThat(error.code()).isEqualTo("INVALID_TAX_ID");
                    assertThat(error.field()).isEqualTo("taxId");
                });
    }
}
