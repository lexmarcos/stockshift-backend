package br.com.stockshift.service.sale;

import br.com.stockshift.dto.sale.SalesDashboardResponse;
import br.com.stockshift.exception.ForbiddenException;
import br.com.stockshift.repository.SaleRepository;
import br.com.stockshift.security.SecurityUtils;
import br.com.stockshift.security.TenantContext;
import br.com.stockshift.service.WarehouseAccessService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.Date;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SalesDashboardServiceTest {

    private static final LocalDate FIXED_TODAY = LocalDate.of(2026, 4, 15);
    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-04-15T12:00:00Z"),
            ZoneOffset.UTC);

    @Mock
    private SaleRepository saleRepository;
    @Mock
    private WarehouseAccessService warehouseAccessService;
    @Mock
    private SecurityUtils securityUtils;

    private SalesDashboardService service;
    private UUID tenantId;
    private UUID warehouseId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        warehouseId = UUID.randomUUID();
        TenantContext.setTenantId(tenantId);
        service = new SalesDashboardService(
                saleRepository, FIXED_CLOCK, warehouseAccessService, securityUtils);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void getDashboardShouldBuildKpisAndFillDailyChartGaps() {
        LocalDate today = FIXED_TODAY;
        when(saleRepository.countAndRevenueByPeriod(eq(tenantId), eq(warehouseId), any(), any()))
                .thenReturn(List.<Object[]>of(new Object[] { 4L, 2000L }));
        when(saleRepository.dailySalesInPeriod(eq(tenantId), eq(warehouseId), any(), any()))
                .thenReturn(List.of(
                        new Object[] { Date.valueOf(today.withDayOfMonth(1)), 2L, 700L },
                        new Object[] { today, 1L, 300L }));

        SalesDashboardResponse response = service.getDashboard(warehouseId);

        assertThat(response.getKpis().getToday().getCount()).isEqualTo(4);
        assertThat(response.getKpis().getToday().getRevenue()).isEqualTo(2000L);
        assertThat(response.getKpis().getToday().getAvgTicket()).isEqualTo(500L);
        assertThat(response.getDailyChart()).isNotEmpty();
        assertThat(response.getDailyChart().get(0).getCount()).isEqualTo(2);
        assertThat(response.getDailyChart())
                .anyMatch(entry -> entry.getDate().equals(today.toString()) && entry.getRevenue() == 300L);
    }

    @Test
    void getDashboardShouldReturnZeroKpisWhenRepositoryHasNoRows() {
        when(warehouseAccessService.hasFullAccess()).thenReturn(true);
        when(saleRepository.countAndRevenueByPeriod(eq(tenantId), eq(null), any(), any()))
                .thenReturn(List.<Object[]>of(new Object[] { null, null }));
        when(saleRepository.dailySalesInPeriod(eq(tenantId), eq(null), any(), any()))
                .thenReturn(List.of());

        SalesDashboardResponse response = service.getDashboard(null);

        assertThat(response.getKpis().getToday().getCount()).isZero();
        assertThat(response.getKpis().getToday().getRevenue()).isZero();
        assertThat(response.getKpis().getToday().getAvgTicket()).isZero();
        assertThat(response.getDailyChart())
                .allMatch(entry -> entry.getCount() == 0L && entry.getRevenue() == 0L);
    }

    @Test
    void getDashboardWithoutWarehouseShouldUseCurrentWarehouseForRegularUser() {
        when(securityUtils.getCurrentWarehouseId()).thenReturn(warehouseId);
        when(saleRepository.countAndRevenueByPeriod(eq(tenantId), eq(warehouseId), any(), any()))
                .thenReturn(List.<Object[]>of(new Object[] { null, null }));
        when(saleRepository.dailySalesInPeriod(eq(tenantId), eq(warehouseId), any(), any()))
                .thenReturn(List.of());

        SalesDashboardResponse response = service.getDashboard(null);

        assertThat(response.getKpis().getToday().getCount()).isZero();
        verify(warehouseAccessService).validateWarehouseAccess(warehouseId);
    }

    @Test
    void getDashboardWithoutWarehouseShouldKeepTenantWideQueryForFullAccessUser() {
        when(warehouseAccessService.hasFullAccess()).thenReturn(true);
        when(saleRepository.countAndRevenueByPeriod(eq(tenantId), isNull(), any(), any()))
                .thenReturn(List.<Object[]>of(new Object[] { null, null }));
        when(saleRepository.dailySalesInPeriod(eq(tenantId), isNull(), any(), any()))
                .thenReturn(List.of());

        service.getDashboard(null);

        verify(securityUtils, never()).getCurrentWarehouseId();
    }

    @Test
    void getDashboardShouldRejectRequestedWarehouseOutsideScope() {
        UUID otherWarehouseId = UUID.randomUUID();
        doThrow(new ForbiddenException("Requested warehouse is outside current token scope"))
                .when(warehouseAccessService).validateWarehouseAccess(otherWarehouseId);

        assertThatThrownBy(() -> service.getDashboard(otherWarehouseId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("outside current token scope");

        verify(saleRepository, never()).countAndRevenueByPeriod(any(), any(), any(), any());
    }
}
