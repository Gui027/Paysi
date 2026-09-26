package com.paysi.sales.web;

import com.paysi.identity.domain.InitialMode;
import com.paysi.identity.session.app.AuthenticatedSession;
import com.paysi.identity.session.app.SessionService;
import com.paysi.identity.session.app.SessionView;
import com.paysi.sales.app.SalesModels.SaleRow;
import com.paysi.sales.app.SalesModels.SalesFilter;
import com.paysi.sales.app.SalesModels.SalesPage;
import com.paysi.sales.app.SalesModels.SalesSummary;
import com.paysi.sales.app.SalesService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SalesController.class)
class SalesControllerTest {
    private static final UUID SELLER = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-09-26T12:00:00Z");

    @Autowired MockMvc mvc;
    @MockitoBean SalesService sales;
    @MockitoBean SessionService sessions;

    @BeforeEach
    void authenticate() {
        when(sessions.authenticate("cookie")).thenReturn(new AuthenticatedSession("cookie",
                new SessionView(SELLER, InitialMode.SELLER, NOW, NOW.plusSeconds(3600))));
    }

    private static SaleRow row() {
        return new SaleRow(UUID.randomUUID(), "3F9A2C1", NOW, NOW, "PAID", "Curso", UUID.randomUUID(), "Oferta",
                "PIX", 1, "Maria", "maria@exemplo.com", 2_235);
    }

    @Test
    void listsSalesWithSummaryAndNumberedPages() throws Exception {
        SalesFilter filter = new SalesFilter(true, null, Set.of(), null, null, null, null);
        when(sales.filter(eq("approved"), isNull(), any(), isNull(), isNull(), isNull(), isNull())).thenReturn(filter);
        when(sales.list(SELLER, filter, 2, null)).thenReturn(new SalesPage(List.of(row()), 2, 10, 326, 33,
                new SalesSummary(326, 467_086)));

        mvc.perform(get("/v1/sales").param("page", "2").cookie(new Cookie("paysi_session", "cookie")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPages").value(33))
                .andExpect(jsonPath("$.summary.netCents").value(467_086))
                .andExpect(jsonPath("$.items[0].code").value("3F9A2C1"))
                .andExpect(jsonPath("$.items[0].netCents").value(2_235));
    }

    @Test
    void exportsCsvAsAttachment() throws Exception {
        SalesFilter filter = new SalesFilter(false, null, Set.of(), null, null, null, null);
        when(sales.filter(eq("all"), isNull(), any(), isNull(), isNull(), isNull(), isNull())).thenReturn(filter);
        when(sales.export(SELLER, filter)).thenReturn(List.of(row()));

        mvc.perform(get("/v1/sales/export").param("tab", "all").cookie(new Cookie("paysi_session", "cookie")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")))
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("3F9A2C1")));
        verify(sales).export(SELLER, filter);
    }
}
