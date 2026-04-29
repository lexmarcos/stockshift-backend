package br.com.stockshift.mapper;

import br.com.stockshift.dto.sale.SaleResponse;
import br.com.stockshift.dto.sale.SaleSummaryResponse;
import br.com.stockshift.model.entity.Sale;
import br.com.stockshift.model.entity.SaleItem;
import br.com.stockshift.model.enums.PaymentMethod;
import br.com.stockshift.model.enums.PaymentMode;
import br.com.stockshift.model.enums.SaleStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SaleMapperTest {

    private final SaleMapper mapper = new SaleMapper();

    @Test
    void toResponseShouldMapSaleAndItems() {
        Sale sale = sale();
        SaleItem item = item();
        sale.addItem(item);

        SaleResponse response = mapper.toResponse(sale, "Central");

        assertThat(response.getId()).isEqualTo(sale.getId());
        assertThat(response.getCode()).isEqualTo("VND-2026-0001");
        assertThat(response.getWarehouseName()).isEqualTo("Central");
        assertThat(response.getPaymentMethod()).isEqualTo(PaymentMethod.PIX);
        assertThat(response.getPaymentMode()).isEqualTo(PaymentMode.LINK);
        assertThat(response.getPaymentLink()).isEqualTo("https://pay.example");
        assertThat(response.getCreatedAt()).isNotNull();
        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().get(0).getBatchCode()).isEqualTo("B1");
    }

    @Test
    void toSummaryResponseShouldMapFallbackFieldsAndNullCreatedAt() {
        Sale sale = sale();
        sale.setCreatedAt(null);

        SaleSummaryResponse response = mapper.toSummaryResponse(sale, "Central", "Seller");

        assertThat(response.getId()).isEqualTo(sale.getId());
        assertThat(response.getWarehouseName()).isEqualTo("Central");
        assertThat(response.getCreatedByUserName()).isEqualTo("Seller");
        assertThat(response.getCreatedAt()).isNull();
        assertThat(response.getPaymentMode()).isEqualTo(PaymentMode.LINK);
    }

    @Test
    void toItemResponseListShouldMapAllItems() {
        List<?> responses = mapper.toItemResponseList(List.of(item(), item()));

        assertThat(responses).hasSize(2);
    }

    private Sale sale() {
        Sale sale = Sale.builder()
                .code("VND-2026-0001")
                .warehouseId(UUID.randomUUID())
                .paymentMethod(PaymentMethod.PIX)
                .installments(1)
                .discountPercentage(new BigDecimal("5"))
                .subtotal(1000L)
                .discountAmount(50L)
                .total(950L)
                .status(SaleStatus.PENDING)
                .createdByUserId(UUID.randomUUID())
                .cancelledByUserId(UUID.randomUUID())
                .cancelledAt(Instant.now())
                .cancellationReason("reason")
                .infinitepayNsu("nsu")
                .infinitepayAut("aut")
                .infinitepayCardBrand("visa")
                .paymentMode(PaymentMode.LINK)
                .paymentLink("https://pay.example")
                .build();
        sale.setId(UUID.randomUUID());
        sale.setCreatedAt(LocalDateTime.now());
        return sale;
    }

    private SaleItem item() {
        SaleItem item = SaleItem.builder()
                .productId(UUID.randomUUID())
                .productName("Product")
                .productSku("SKU")
                .batchId(UUID.randomUUID())
                .batchCode("B1")
                .quantity(new BigDecimal("2"))
                .unitPrice(500L)
                .totalPrice(1000L)
                .build();
        item.setId(UUID.randomUUID());
        return item;
    }
}
