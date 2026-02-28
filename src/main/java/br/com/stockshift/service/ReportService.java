package br.com.stockshift.service;

import br.com.stockshift.dto.report.DashboardResponse;
import br.com.stockshift.dto.report.StockReportResponse;
import br.com.stockshift.exception.UnauthorizedException;
import br.com.stockshift.model.entity.Batch;
import br.com.stockshift.repository.BatchRepository;
import br.com.stockshift.security.SecurityUtils;
import br.com.stockshift.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportService {

    private final BatchRepository batchRepository;
    private final SecurityUtils securityUtils;
    private final WarehouseAccessService warehouseAccessService;

    @Transactional(readOnly = true)
    public DashboardResponse getDashboard() {
        UUID tenantId = TenantContext.getTenantId();
        UUID currentWarehouseId = resolveCurrentWarehouseId();

        List<Batch> allBatches;
        if (currentWarehouseId != null) {
            allBatches = batchRepository.findByWarehouseIdAndTenantId(currentWarehouseId, tenantId);
        } else if (warehouseAccessService.hasFullAccess()) {
            allBatches = batchRepository.findAllByTenantId(tenantId);
        } else {
            throw new UnauthorizedException("No active warehouse context");
        }

        long totalProducts = allBatches.stream()
                .map(batch -> batch.getProduct().getId())
                .distinct()
                .count();
        long totalWarehouses = 1;

        BigDecimal totalStockQuantity = allBatches.stream()
                .map(Batch::getQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalStockValue = allBatches.stream()
                .filter(b -> b.getCostPrice() != null)
                .map(b -> BigDecimal.valueOf(b.getCostPrice()).multiply(b.getQuantity()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<StockReportResponse> lowStockProducts = getLowStockReport(10, 10);
        List<StockReportResponse> expiringProducts = getExpiringProductsReport(30, 10);

        return DashboardResponse.builder()
                .totalProducts(totalProducts)
                .totalWarehouses(totalWarehouses)
                .totalStockQuantity(totalStockQuantity)
                .totalStockValue(totalStockValue)
                .lowStockProducts(lowStockProducts)
                .expiringProducts(expiringProducts)
                .build();
    }

    @Transactional(readOnly = true)
    public List<StockReportResponse> getStockReport() {
        UUID tenantId = TenantContext.getTenantId();
        UUID currentWarehouseId = resolveCurrentWarehouseId();
        List<Batch> batches;
        if (currentWarehouseId != null) {
            batches = batchRepository.findByWarehouseIdAndTenantId(currentWarehouseId, tenantId);
        } else if (warehouseAccessService.hasFullAccess()) {
            batches = batchRepository.findAllByTenantId(tenantId);
        } else {
            throw new UnauthorizedException("No active warehouse context");
        }

        Map<String, List<Batch>> groupedBatches = batches.stream()
                .collect(Collectors.groupingBy(b ->
                    b.getProduct().getId().toString() + "_" + b.getWarehouse().getId().toString()
                ));

        return groupedBatches.values().stream()
                .map(this::aggregateBatches)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<StockReportResponse> getLowStockReport(Integer threshold, Integer limit) {
        UUID tenantId = TenantContext.getTenantId();
        UUID currentWarehouseId = resolveCurrentWarehouseId();
        List<Batch> lowStockBatches = batchRepository.findLowStock(threshold, tenantId);
        if (currentWarehouseId != null) {
            lowStockBatches = lowStockBatches.stream()
                    .filter(batch -> currentWarehouseId.equals(batch.getWarehouse().getId()))
                    .collect(Collectors.toList());
        } else if (!warehouseAccessService.hasFullAccess()) {
            throw new UnauthorizedException("No active warehouse context");
        }

        return lowStockBatches.stream()
                .limit(limit != null ? limit : Long.MAX_VALUE)
                .map(this::batchToReport)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<StockReportResponse> getExpiringProductsReport(Integer daysAhead, Integer limit) {
        UUID tenantId = TenantContext.getTenantId();
        UUID currentWarehouseId = resolveCurrentWarehouseId();
        LocalDate startDate = LocalDate.now();
        LocalDate endDate = startDate.plusDays(daysAhead);

        List<Batch> expiringBatches = batchRepository.findExpiringBatches(startDate, endDate, tenantId);
        if (currentWarehouseId != null) {
            expiringBatches = expiringBatches.stream()
                    .filter(batch -> currentWarehouseId.equals(batch.getWarehouse().getId()))
                    .collect(Collectors.toList());
        } else if (!warehouseAccessService.hasFullAccess()) {
            throw new UnauthorizedException("No active warehouse context");
        }

        return expiringBatches.stream()
                .limit(limit != null ? limit : Long.MAX_VALUE)
                .map(this::batchToReport)
                .collect(Collectors.toList());
    }

    private StockReportResponse aggregateBatches(List<Batch> batches) {
        Batch first = batches.get(0);

        BigDecimal totalQuantity = batches.stream()
                .map(Batch::getQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalValue = batches.stream()
                .filter(b -> b.getCostPrice() != null)
                .map(b -> BigDecimal.valueOf(b.getCostPrice()).multiply(b.getQuantity()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        LocalDate nearestExpiration = batches.stream()
                .map(Batch::getExpirationDate)
                .filter(date -> date != null)
                .min(LocalDate::compareTo)
                .orElse(null);

        return StockReportResponse.builder()
                .productId(first.getProduct().getId())
                .productName(first.getProduct().getName())
                .warehouseId(first.getWarehouse().getId())
                .warehouseName(first.getWarehouse().getName())
                .totalQuantity(totalQuantity)
                .totalValue(totalValue)
                .nearestExpiration(nearestExpiration)
                .batchCount(batches.size())
                .build();
    }

    private StockReportResponse batchToReport(Batch batch) {
        BigDecimal totalValue = batch.getCostPrice() != null ?
                BigDecimal.valueOf(batch.getCostPrice()).multiply(batch.getQuantity()) :
                BigDecimal.ZERO;

        return StockReportResponse.builder()
                .productId(batch.getProduct().getId())
                .productName(batch.getProduct().getName())
                .warehouseId(batch.getWarehouse().getId())
                .warehouseName(batch.getWarehouse().getName())
                .totalQuantity(batch.getQuantity())
                .totalValue(totalValue)
                .nearestExpiration(batch.getExpirationDate())
                .batchCount(1)
                .build();
    }

    private UUID resolveCurrentWarehouseId() {
        try {
            return securityUtils.getCurrentWarehouseId();
        } catch (UnauthorizedException ex) {
            return null;
        }
    }
}
