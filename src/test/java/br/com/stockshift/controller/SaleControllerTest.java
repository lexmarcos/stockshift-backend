package br.com.stockshift.controller;

import br.com.stockshift.service.sale.SaleService;
import br.com.stockshift.service.sale.SalesDashboardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class SaleControllerTest {

    @Mock
    private SaleService saleService;

    @Mock
    private SalesDashboardService salesDashboardService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        SaleController controller = new SaleController(saleService, salesDashboardService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
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
}
