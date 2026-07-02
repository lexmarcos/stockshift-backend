package br.com.stockshift.service.sale;

import br.com.stockshift.dto.sale.*;
import br.com.stockshift.repository.SaleRepository;
import br.com.stockshift.security.SecurityUtils;
import br.com.stockshift.security.TenantContext;
import br.com.stockshift.service.WarehouseAccessService;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
@Slf4j
public class SalesDashboardService {

    private final SaleRepository saleRepository;
    private final Clock clock;
    private final WarehouseAccessService warehouseAccessService;
    private final SecurityUtils securityUtils;

    @Autowired
    public SalesDashboardService(
            SaleRepository saleRepository,
            WarehouseAccessService warehouseAccessService,
            SecurityUtils securityUtils) {
        this(saleRepository, Clock.systemDefaultZone(), warehouseAccessService, securityUtils);
    }

    SalesDashboardService(
            SaleRepository saleRepository,
            Clock clock,
            WarehouseAccessService warehouseAccessService,
            SecurityUtils securityUtils) {
        this.saleRepository = saleRepository;
        this.clock = clock;
        this.warehouseAccessService = warehouseAccessService;
        this.securityUtils = securityUtils;
    }

    @Transactional(readOnly = true)
    public SalesDashboardResponse getDashboard(UUID warehouseId) {
        UUID tenantId = TenantContext.getTenantId();
        UUID effectiveWarehouseId = resolveReadableWarehouseFilter(warehouseId);
        LocalDate today = LocalDate.now(clock);

        KpiPeriod todayKpi = buildKpi(tenantId, effectiveWarehouseId,
                today.atStartOfDay(), today.plusDays(1).atStartOfDay());
        KpiPeriod weekKpi = buildKpi(tenantId, effectiveWarehouseId,
                today.with(DayOfWeek.MONDAY).atStartOfDay(), today.plusDays(1).atStartOfDay());
        KpiPeriod monthKpi = buildKpi(tenantId, effectiveWarehouseId,
                today.withDayOfMonth(1).atStartOfDay(), today.plusDays(1).atStartOfDay());

        List<Object[]> rawChart = saleRepository.dailySalesInPeriod(
                tenantId, effectiveWarehouseId,
                today.withDayOfMonth(1).atStartOfDay(),
                today.plusDays(1).atStartOfDay());

        Map<LocalDate, Object[]> chartMap = new LinkedHashMap<>();
        for (Object[] row : rawChart) {
            LocalDate date = row[0] instanceof java.sql.Date sd ? sd.toLocalDate() : (LocalDate) row[0];
            chartMap.put(date, row);
        }

        List<DailyChartEntry> dailyChart = new ArrayList<>();
        LocalDate start = today.withDayOfMonth(1);
        for (int i = 0; i < today.lengthOfMonth(); i++) {
            LocalDate d = start.plusDays(i);
            if (d.isAfter(today)) break;
            Object[] row = chartMap.get(d);
            dailyChart.add(DailyChartEntry.builder()
                    .date(d.toString())
                    .count(row != null ? ((Number) row[1]).longValue() : 0L)
                    .revenue(row != null ? ((Number) row[2]).longValue() : 0L)
                    .build());
        }

        return SalesDashboardResponse.builder()
                .kpis(SalesDashboardResponse.Kpis.builder()
                        .today(todayKpi)
                        .week(weekKpi)
                        .month(monthKpi)
                        .build())
                .dailyChart(dailyChart)
                .build();
    }

    private UUID resolveReadableWarehouseFilter(UUID requestedWarehouseId) {
        if (requestedWarehouseId != null) {
            warehouseAccessService.validateWarehouseAccess(requestedWarehouseId);
            return requestedWarehouseId;
        }
        if (warehouseAccessService.hasFullAccess()) {
            return null;
        }
        UUID currentWarehouseId = securityUtils.getCurrentWarehouseId();
        warehouseAccessService.validateWarehouseAccess(currentWarehouseId);
        return currentWarehouseId;
    }

    private KpiPeriod buildKpi(UUID tenantId, UUID warehouseId, LocalDateTime from, LocalDateTime to) {
        List<Object[]> result = saleRepository.countAndRevenueByPeriod(tenantId, warehouseId, from, to);
        if (result == null || result.isEmpty() || result.get(0)[0] == null) {
            return KpiPeriod.builder().count(0).revenue(0L).avgTicket(0L).build();
        }
        Object[] row = result.get(0);
        long count = ((Number) row[0]).longValue();
        long revenue = ((Number) row[1]).longValue();
        long avgTicket = count > 0 ? revenue / count : 0L;
        return KpiPeriod.builder().count(count).revenue(revenue).avgTicket(avgTicket).build();
    }
}
