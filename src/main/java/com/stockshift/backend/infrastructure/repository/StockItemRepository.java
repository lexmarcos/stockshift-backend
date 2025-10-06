package com.stockshift.backend.infrastructure.repository;

import com.stockshift.backend.domain.stock.StockItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StockItemRepository extends JpaRepository<StockItem, UUID> {

    Optional<StockItem> findByWarehouseIdAndVariantId(UUID warehouseId, UUID variantId);

    List<StockItem> findByWarehouseIdAndVariantIdIn(UUID warehouseId, List<UUID> variantIds);
}
