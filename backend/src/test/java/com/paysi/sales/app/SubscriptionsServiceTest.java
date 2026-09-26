package com.paysi.sales.app;

import com.paysi.core.error.NotFoundException;
import com.paysi.core.error.ValidationException;
import com.paysi.sales.app.SubscriptionsModels.SubscriptionRow;
import com.paysi.sales.app.SubscriptionsModels.SubscriptionsFilter;
import com.paysi.sales.app.SubscriptionsModels.SubscriptionsSummary;
import com.paysi.sales.port.SubscriptionsQueryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SubscriptionsServiceTest {
    private static final UUID SELLER = UUID.randomUUID();
    private static final UUID SUBSCRIPTION = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-09-26T12:00:00Z");

    private SubscriptionsQueryRepository repository;
    private SubscriptionsService service;

    @BeforeEach
    void setUp() {
        repository = mock(SubscriptionsQueryRepository.class);
        service = new SubscriptionsService(repository);
    }

    @Test
    void defaultsToActiveTabAndValidatesEveryUrlValue() {
        SubscriptionsFilter filter = service.filter(null, " maria ", List.of("active", ""), "monthly", "pix", null, "2026-09-01", "2026-09-30");
        assertThat(filter.tab()).isEqualTo("active");
        assertThat(filter.query()).isEqualTo("maria");
        assertThat(filter.statuses()).containsExactly("ACTIVE");
        assertThat(filter.cycle()).isEqualTo("MONTHLY");
        assertThat(filter.method()).isEqualTo("PIX");

        assertThatThrownBy(() -> service.filter("todas", null, null, null, null, null, null, null)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> service.filter("all", null, List.of("X"), null, null, null, null, null)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> service.filter("all", null, null, "WEEKLY", null, null, null, null)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> service.filter("all", null, null, null, "CASH", null, null, null)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> service.filter("all", null, null, null, null, null, "2026-09-30", "2026-09-01")).isInstanceOf(ValidationException.class);
    }

    @Test
    void paginatesAndReturnsTheSummaryComputedByTheDatabase() {
        SubscriptionsFilter filter = service.filter("all", null, null, null, null, null, null, null);
        when(repository.count(SELLER, filter)).thenReturn(21L);
        when(repository.summarize(SELLER, filter)).thenReturn(new SubscriptionsSummary(1, 4_342));
        when(repository.list(eq(SELLER), eq(filter), eq(10), anyInt())).thenReturn(List.of());

        var page = service.list(SELLER, filter, 9, null);

        assertThat(page.totalPages()).isEqualTo(3);
        assertThat(page.page()).isEqualTo(3);
        assertThat(page.summary().monthlyRecurringCents()).isEqualTo(4_342);
        verify(repository).list(SELLER, filter, 10, 20);
    }

    @Test
    void detailOfSomeoneElsesSubscriptionIsNotFound() {
        when(repository.find(SELLER, SUBSCRIPTION)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.detail(SELLER, SUBSCRIPTION)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void csvShowsScreenLabelsAndBlankNetForTrials() {
        var active = new SubscriptionRow(SUBSCRIPTION, "3F9A2C1", NOW, "ACTIVE", false, "Rangu", UUID.randomUUID(), null,
                "MONTHLY", "VERA & \"CLEICA\"", "lu@x.com", 4_342L, Instant.parse("2026-10-17T12:00:00Z"));
        var trial = new SubscriptionRow(UUID.randomUUID(), "AAAAAAA", NOW, "TRIAL", false, "Rangu", UUID.randomUUID(), "Pro",
                "ANNUAL", "=Ana", "ana@x.com", null, null);
        var scheduled = new SubscriptionRow(UUID.randomUUID(), "BBBBBBB", NOW, "ACTIVE", true, "Rangu", UUID.randomUUID(), null,
                "QUARTERLY", "Bia", "bia@x.com", 1_000L, null);
        String csv = SubscriptionsCsv.build(List.of(active, trial, scheduled));

        assertThat(csv).startsWith("﻿ID da assinatura;Data de início;");
        assertThat(csv).contains(";Ativo;Mensal;43,42;17/10/2026");
        assertThat(csv).contains(";Em teste;Anual;;");
        assertThat(csv).contains("'=Ana");
        assertThat(csv).contains(";Cancelamento agendado;Trimestral;10,00;");
    }
}
