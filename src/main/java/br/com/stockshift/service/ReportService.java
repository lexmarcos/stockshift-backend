package br.com.stockshift.service;

import br.com.stockshift.dto.report.DashboardResponse;
import br.com.stockshift.dto.report.StockReportResponse;
import br.com.stockshift.model.entity.Batch;
import br.com.stockshift.model.enums.MovementStatus;
import br.com.stockshift.repository.*;
import br.com.stockshift.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportService {

    private final ProductRepository productRepository;
    private final WarehouseRepository warehouseRepository;
    private final BatchRepository batchRepository;
    private final StockMovementRepository stockMovementRepository;

    @Transactional(readOnly = true)
    public DashboardResponse getDashboard() {
        UUID tenantId = TenantContext.getTenantId();

        long totalProducts = productRepository.findAllByTenantId(tenantId).size();
        long totalWarehouses = warehouseRepository.findAllByTenantId(tenantId).size();

        List<Batch> allBatches = batchRepository.findAllByTenantId(tenantId);

        int totalStockQuantity = allBatches.stream()
                .mapToInt(Batch::getQuantity)
                .sum();

        BigDecimal totalStockValue = allBatches.stream()
                .filter(b -> b.getCostPrice() != null)
                .map(b -> b.getCostPrice().multiply(BigDecimal.valueOf(b.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long pendingMovements = stockMovementRepository.findByTenantIdAndStatus(tenantId, MovementStatus.PENDING).size();

        LocalDateTime todayStart = LocalDateTime.now().toLocalDate().atStartOfDay();
        LocalDateTime todayEnd = todayStart.plusDays(1);
        long completedMovementsToday = stockMovementRepository.findByTenantIdAndDateRange(tenantId, todayStart, todayEnd)
                .stream()
                .filter(m -> m.getStatus() == MovementStatus.COMPLETED)
                .count();

        List<StockReportResponse> lowStockProducts = getLowStockReport(10, 10);
        List<StockReportResponse> expiringProducts = getExpiringProductsReport(30, 10);

        return DashboardResponse.builder()
                .totalProducts(totalProducts)
                .totalWarehouses(totalWarehouses)
                .totalStockQuantity(totalStockQuantity)
                .totalStockValue(totalStockValue)
                .pendingMovements(pendingMovements)
                .completedMovementsToday(completedMovementsToday)
                .lowStockProducts(lowStockProducts)
                .expiringProducts(expiringProducts)
                .build();
    }

    @Transactional(readOnly = true)
    public List<StockReportResponse> getStockReport() {
        UUID tenantId = TenantContext.getTenantId();
        List<Batch> batches = batchRepository.findAllByTenantId(tenantId);

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
        List<Batch> lowStockBatches = batchRepository.findLowStock(threshold, tenantId);

        return lowStockBatches.stream()
                .limit(limit != null ? limit : Long.MAX_VALUE)
                .map(this::batchToReport)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<StockReportResponse> getExpiringProductsReport(Integer daysAhead, Integer limit) {
        UUID tenantId = TenantContext.getTenantId();
        LocalDate startDate = LocalDate.now();
        LocalDate endDate = startDate.plusDays(daysAhead);

        List<Batch> expiringBatches = batchRepository.findExpiringBatches(startDate, endDate, tenantId);

        return expiringBatches.stream()
                .limit(limit != null ? limit : Long.MAX_VALUE)
                .map(this::batchToReport)
                .collect(Collectors.toList());
    }

    private StockReportResponse aggregateBatches(List<Batch> batches) {
        Batch first = batches.get(0);

        int totalQuantity = batches.stream()
                .mapToInt(Batch::getQuantity)
                .sum();

        BigDecimal totalValue = batches.stream()
                .filter(b -> b.getCostPrice() != null)
                .map(b -> b.getCostPrice().multiply(BigDecimal.valueOf(b.getQuantity())))
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
                batch.getCostPrice().multiply(BigDecimal.valueOf(batch.getQuantity())) :
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
}
