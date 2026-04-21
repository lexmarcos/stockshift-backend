package br.com.stockshift.mapper;

import br.com.stockshift.dto.sale.*;
import br.com.stockshift.model.entity.Sale;
import br.com.stockshift.model.entity.SaleItem;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class SaleMapper {

    public SaleResponse toResponse(Sale sale, String warehouseName) {
        return SaleResponse.builder()
                .id(sale.getId())
                .code(sale.getCode())
                .warehouseId(sale.getWarehouseId())
                .warehouseName(warehouseName)
                .paymentMethod(sale.getPaymentMethod())
                .installments(sale.getInstallments())
                .discountPercentage(sale.getDiscountPercentage())
                .subtotal(sale.getSubtotal())
                .discountAmount(sale.getDiscountAmount())
                .total(sale.getTotal())
                .status(sale.getStatus())
                .cancelledByUserId(sale.getCancelledByUserId())
                .cancelledAt(sale.getCancelledAt())
                .cancellationReason(sale.getCancellationReason())
                .createdByUserId(sale.getCreatedByUserId())
                .createdAt(sale.getCreatedAt() != null ? sale.getCreatedAt().atZone(java.time.ZoneId.systemDefault()).toInstant() : null)
                .items(toItemResponseList(sale.getItems()))
                .infinitepayNsu(sale.getInfinitepayNsu())
                .infinitepayAut(sale.getInfinitepayAut())
                .infinitepayCardBrand(sale.getInfinitepayCardBrand())
                .paymentMode(sale.getPaymentMode())
                .paymentLink(sale.getPaymentLink())
                .build();
    }

    public SaleItemResponse toItemResponse(SaleItem item) {
        return SaleItemResponse.builder()
                .id(item.getId())
                .productId(item.getProductId())
                .productName(item.getProductName())
                .productSku(item.getProductSku())
                .batchId(item.getBatchId())
                .batchCode(item.getBatchCode())
                .quantity(item.getQuantity())
                .unitPrice(item.getUnitPrice())
                .totalPrice(item.getTotalPrice())
                .build();
    }

    public List<SaleItemResponse> toItemResponseList(List<SaleItem> items) {
        return items.stream().map(this::toItemResponse).collect(Collectors.toList());
    }

    public SaleSummaryResponse toSummaryResponse(Sale sale, String warehouseName, String createdByUserName) {
        return SaleSummaryResponse.builder()
                .id(sale.getId())
                .code(sale.getCode())
                .warehouseId(sale.getWarehouseId())
                .warehouseName(warehouseName)
                .paymentMethod(sale.getPaymentMethod())
                .total(sale.getTotal())
                .status(sale.getStatus())
                .createdAt(sale.getCreatedAt() != null ? sale.getCreatedAt().atZone(java.time.ZoneId.systemDefault()).toInstant() : null)
                .createdByUserName(createdByUserName)
                .paymentMode(sale.getPaymentMode())
                .build();
    }
}
