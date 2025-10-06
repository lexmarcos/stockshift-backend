package com.stockshift.backend.api.mapper;

import com.stockshift.backend.api.dto.report.ExpiringItemResponse;
import com.stockshift.backend.api.dto.report.LowStockItemResponse;
import com.stockshift.backend.api.dto.report.StockHistoryEntryResponse;
import com.stockshift.backend.api.dto.report.StockSnapshotItemResponse;
import com.stockshift.backend.domain.report.ExpiringItemView;
import com.stockshift.backend.domain.report.LowStockView;
import com.stockshift.backend.domain.report.StockHistoryEntry;
import com.stockshift.backend.domain.report.StockSnapshotView;
import org.springframework.stereotype.Component;

@Component
public class ReportMapper {

    public StockSnapshotItemResponse toResponse(StockSnapshotView view) {
        return StockSnapshotItemResponse.builder()
                .variantId(view.variantId())
                .sku(view.sku())
                .productId(view.productId())
                .productName(view.productName())
                .brandId(view.brandId())
                .brandName(view.brandName())
                .categoryId(view.categoryId())
                .categoryName(view.categoryName())
                .warehouseId(view.warehouseId())
                .warehouseName(view.warehouseName())
                .quantity(view.quantity())
                .asOf(view.asOf())
                .build();
    }

    public StockHistoryEntryResponse toResponse(StockHistoryEntry entry) {
        return StockHistoryEntryResponse.builder()
                .eventId(entry.eventId())
                .eventType(entry.eventType())
                .warehouseId(entry.warehouseId())
                .warehouseName(entry.warehouseName())
                .occurredAt(entry.occurredAt())
                .quantityChange(entry.quantityChange())
                .balanceBefore(entry.balanceBefore())
                .balanceAfter(entry.balanceAfter())
                .reasonCode(entry.reasonCode())
                .notes(entry.notes())
                .build();
    }

    public LowStockItemResponse toResponse(LowStockView view) {
        return LowStockItemResponse.builder()
                .variantId(view.variantId())
                .sku(view.sku())
                .productId(view.productId())
                .productName(view.productName())
                .brandId(view.brandId())
                .brandName(view.brandName())
                .categoryId(view.categoryId())
                .categoryName(view.categoryName())
                .warehouseId(view.warehouseId())
                .warehouseName(view.warehouseName())
                .quantity(view.quantity())
                .threshold(view.threshold())
                .deficit(view.deficit())
                .build();
    }

    public ExpiringItemResponse toResponse(ExpiringItemView view) {
        return ExpiringItemResponse.builder()
                .variantId(view.variantId())
                .sku(view.sku())
                .productId(view.productId())
                .productName(view.productName())
                .brandId(view.brandId())
                .brandName(view.brandName())
                .categoryId(view.categoryId())
                .categoryName(view.categoryName())
                .warehouseId(view.warehouseId())
                .warehouseName(view.warehouseName())
                .quantity(view.quantity())
                .expiryDate(view.expiryDate())
                .daysUntilExpiry(view.daysUntilExpiry())
                .build();
    }
}
