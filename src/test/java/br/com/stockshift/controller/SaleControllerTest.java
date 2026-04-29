package br.com.stockshift.controller;

import br.com.stockshift.service.sale.SaleService;
import br.com.stockshift.service.sale.SalesDashboardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class SaleControllerTest {

    @Mock
    private SaleService saleService;

    @Mock
    private SalesDashboardService salesDashboardService;

    private MockMvc mockMvc;
    private SaleController controller;

    @BeforeEach
    void setUp() {
        controller = new SaleController(saleService, salesDashboardService);
        ReflectionTestUtils.setField(controller, "frontendUrl", "https://app.example");
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();
    }

    @Test
    void shouldDelegateCreateListReadCancelAndDashboardEndpoints() throws Exception {
        UUID saleId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();

        when(saleService.create(any())).thenReturn(br.com.stockshift.dto.sale.SaleResponse.builder()
                .id(saleId)
                .warehouseId(warehouseId)
                .build());
        mockMvc.perform(post("/api/sales")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"warehouseId":"%s","paymentMethod":"CASH","items":[{"productId":"%s","quantity":1}]}
                        """.formatted(warehouseId, UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(saleId.toString()));

        when(saleService.list(eq(warehouseId), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(br.com.stockshift.dto.sale.SaleSummaryResponse.builder()
                        .id(saleId)
                        .build())));
        var listResponse = controller.list(warehouseId, br.com.stockshift.model.enums.PaymentMethod.CASH,
                br.com.stockshift.model.enums.SaleStatus.COMPLETED, null, null, PageRequest.of(0, 20));
        assertThat(listResponse.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(listResponse.getBody().getData().getContent()).hasSize(1);

        when(saleService.getNextCode()).thenReturn(br.com.stockshift.dto.sale.NextSaleCodeResponse.builder()
                .code("VND-2026-0001")
                .build());
        mockMvc.perform(get("/api/sales/next-code"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").value("VND-2026-0001"));

        when(salesDashboardService.getDashboard(warehouseId))
                .thenReturn(br.com.stockshift.dto.sale.SalesDashboardResponse.builder()
                        .dailyChart(List.of())
                        .build());
        mockMvc.perform(get("/api/sales/dashboard").param("warehouseId", warehouseId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        when(saleService.getById(saleId)).thenReturn(br.com.stockshift.dto.sale.SaleResponse.builder()
                .id(saleId)
                .build());
        mockMvc.perform(get("/api/sales/{id}", saleId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(saleId.toString()));

        when(saleService.cancel(eq(saleId), any())).thenReturn(br.com.stockshift.dto.sale.SaleResponse.builder()
                .id(saleId)
                .status(br.com.stockshift.model.enums.SaleStatus.CANCELLED)
                .build());
        mockMvc.perform(put("/api/sales/{id}/cancel", saleId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"cancellationReason\":\"Customer asked\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
    }

    @Test
    void shouldConfirmInfinitePayReturnAsJson() throws Exception {
        UUID saleId = UUID.randomUUID();

        mockMvc.perform(get("/api/sales/infinitepay/confirm")
                .param("order_id", saleId.toString())
                .param("nsu", "nsu-123")
                .param("aut", "aut-456")
                .param("card_brand", "visa"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("success"))
                .andExpect(jsonPath("$.data.saleId").value(saleId.toString()));

        verify(saleService).confirmInfinitePayPayment(saleId, "nsu-123", "aut-456", "visa");
    }

    @Test
    void shouldReturnWarningAsInfinitePayConfirmError() throws Exception {
        UUID saleId = UUID.randomUUID();

        mockMvc.perform(get("/api/sales/infinitepay/confirm")
                .param("order_id", saleId.toString())
                .param("warning", "cancelled"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("error"))
                .andExpect(jsonPath("$.data.saleId").value(saleId.toString()))
                .andExpect(jsonPath("$.data.message").value("cancelled"));

        verifyNoInteractions(saleService);
    }

    @Test
    void shouldReturnInvalidOrderForMalformedInfinitePayOrder() throws Exception {
        mockMvc.perform(get("/api/sales/infinitepay/confirm")
                .param("order_id", "not-a-uuid"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("error"))
                .andExpect(jsonPath("$.data.saleId").doesNotExist())
                .andExpect(jsonPath("$.data.message").value("invalid_order"));

        verifyNoInteractions(saleService);
    }

    @Test
    void shouldRedirectInfinitePayCallbackToFrontendResult() throws Exception {
        UUID saleId = UUID.randomUUID();

        mockMvc.perform(get("/api/sales/infinitepay/callback")
                .param("order_id", saleId.toString())
                .param("nsu", "nsu-123"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location",
                        "https://app.example/sales/infinitepay/result?status=success&sale_id=" + saleId));

        verify(saleService).confirmInfinitePayPayment(eq(saleId), eq("nsu-123"), eq(null), eq(null));
    }

    @Test
    void shouldReturnErrorWhenConfirmServiceFails() throws Exception {
        UUID saleId = UUID.randomUUID();
        doThrow(new RuntimeException("boom"))
                .when(saleService).confirmInfinitePayPayment(eq(saleId), any(), any(), any());

        mockMvc.perform(get("/api/sales/infinitepay/confirm")
                .param("order_id", saleId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("error"))
                .andExpect(jsonPath("$.data.saleId").value(saleId.toString()));
    }

    @Test
    void shouldProcessInfinitePayWebhookAndRejectInvalidWebhookOrder() throws Exception {
        UUID saleId = UUID.randomUUID();

        mockMvc.perform(post("/api/sales/infinitepay/webhook")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"order_nsu":"%s","transaction_nsu":"txn","capture_method":"pix","invoice_slug":"slug","receipt_url":"receipt","installments":1}
                        """.formatted(saleId)))
                .andExpect(status().isOk());

        verify(saleService).confirmInfinitePayWebhook(saleId, "txn", "pix", "slug", "receipt", 1);

        mockMvc.perform(post("/api/sales/infinitepay/webhook")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"order_nsu\":\"bad-id\"}"))
                .andExpect(status().isBadRequest());
    }
}
