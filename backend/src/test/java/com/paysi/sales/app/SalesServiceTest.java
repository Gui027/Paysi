package com.paysi.sales.app;

import com.paysi.core.error.NotFoundException;
import com.paysi.core.error.ValidationException;
import com.paysi.sales.app.SalesModels.Amounts;
import com.paysi.sales.app.SalesModels.Buyer;
import com.paysi.sales.app.SalesModels.PayoutState;
import com.paysi.sales.app.SalesModels.SaleDetail;
import com.paysi.sales.app.SalesModels.SaleRow;
import com.paysi.sales.app.SalesModels.SalesFilter;
import com.paysi.sales.app.SalesModels.SalesSummary;
import com.paysi.sales.port.SalesQueryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SalesServiceTest {
    private static final UUID SELLER = UUID.randomUUID();
    private static final UUID CHARGE = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-09-26T12:00:00Z");

    private SalesQueryRepository repository;
    private SalesService service;

    @BeforeEach
    void setUp() {
        repository = mock(SalesQueryRepository.class);
        service = new SalesService(repository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void defaultsToApprovedTabAndValidatesEveryUrlValue() {
        SalesFilter filter = service.filter(null, "  maria ", List.of("paid", " "), "pix", null, "2026-09-01", "2026-09-30");
        assertThat(filter.approvedOnly()).isTrue();
        assertThat(filter.query()).isEqualTo("maria");
        assertThat(filter.statuses()).containsExactly("PAID");
        assertThat(filter.method()).isEqualTo("PIX");
        assertThat(service.filter("all", null, null, null, null, null, null).approvedOnly()).isFalse();

        assertThatThrownBy(() -> service.filter("all", null, List.of("HACK"), null, null, null, null))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> service.filter("all", null, null, "CASH", null, null, null))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> service.filter("all", null, null, null, null, "ontem", null))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> service.filter("all", null, null, null, null, "2026-09-30", "2026-09-01"))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> service.filter("all", "x".repeat(121), null, null, null, null, null))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void paginatesWithTotalsFromTheDatabaseAndClampsThePage() {
        SalesFilter filter = service.filter("all", null, null, null, null, null, null);
        when(repository.summarize(SELLER, filter)).thenReturn(new SalesSummary(326, 467_086));
        when(repository.list(eq(SELLER), eq(filter), eq(10), anyInt())).thenReturn(List.of());

        var page = service.list(SELLER, filter, 99, null);

        assertThat(page.totalPages()).isEqualTo(33);
        assertThat(page.page()).isEqualTo(33);
        assertThat(page.summary().netCents()).isEqualTo(467_086);
        verify(repository).list(SELLER, filter, 10, 320);
        assertThat(service.list(SELLER, filter, -5, 500).size()).isEqualTo(50);
    }

    @Test
    void detailStatesWhereTheMoneyIs() {
        when(repository.find(SELLER, CHARGE)).thenReturn(Optional.of(detail("PAID", NOW.minusSeconds(86_400), NOW.plusSeconds(86_400))));
        assertThat(service.detail(SELLER, CHARGE).payoutState()).isEqualTo(PayoutState.TO_RELEASE);
        when(repository.find(SELLER, CHARGE)).thenReturn(Optional.of(detail("PAID", NOW.minusSeconds(864_000), NOW.minusSeconds(86_400))));
        assertThat(service.detail(SELLER, CHARGE).payoutState()).isEqualTo(PayoutState.RELEASED);
        when(repository.find(SELLER, CHARGE)).thenReturn(Optional.of(detail("PENDING", null, null)));
        assertThat(service.detail(SELLER, CHARGE).payoutState()).isEqualTo(PayoutState.WAITING_PAYMENT);
        when(repository.find(SELLER, CHARGE)).thenReturn(Optional.of(detail("REFUNDED", NOW, NOW)));
        assertThat(service.detail(SELLER, CHARGE).payoutState()).isEqualTo(PayoutState.REFUNDED);

        when(repository.find(SELLER, CHARGE)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.detail(SELLER, CHARGE)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void refundListValidatesStatusesAndPaginates() {
        when(repository.countRefunds(eq(SELLER), any(), any())).thenReturn(21L);
        var page = service.refunds(SELLER, null, List.of("succeeded"), 3, 10);
        assertThat(page.totalPages()).isEqualTo(3);
        verify(repository).listRefunds(SELLER, null, Set.of("SUCCEEDED"), 10, 20);
        assertThatThrownBy(() -> service.refunds(SELLER, null, List.of("NOPE"), 1, 10)).isInstanceOf(ValidationException.class);
    }

    @Test
    void csvIsExcelFriendlyAndNeutralizesFormulas() {
        var row = new SaleRow(CHARGE, "3F9A2C1", NOW, NOW, "PAID", "=HYPERLINK(\"x\")", UUID.randomUUID(), null,
                "PIX", 1, "Maria; \"Souza\"", "maria@exemplo.com", 2_235);
        String csv = SalesCsv.build(List.of(row));

        assertThat(csv).startsWith("﻿ID da venda;Data;");
        assertThat(csv).contains("'=HYPERLINK(\"\"x\"\")".replace("'=HYPERLINK(\"\"x\"\")", "\"'=HYPERLINK(\"\"x\"\")\""));
        assertThat(csv).contains("\"Maria; \"\"Souza\"\"\"");
        assertThat(csv).contains(";Pago;Pix;1;22,35");
        assertThat(SalesCsv.money(5)).isEqualTo("0,05");
    }

    private static SaleDetail detail(String status, Instant approvedAt, Instant availableAt) {
        return new SaleDetail(CHARGE, "3F9A2C1", status, "PRODUCER", "Curso", UUID.randomUUID(), "Oferta", "PIX", 1,
                NOW.minusSeconds(100_000), approvedAt, availableAt, null, null, null, null,
                new Buyer("Maria", "m@x.com", null, "39053344705", "PF", "10.0.0.1"),
                new Amounts(990, 0, 990, 328, 0, 662, 0, 662), List.of(), null, true, List.of());
    }
}
