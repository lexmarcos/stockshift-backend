package com.stockshift.backend.application.service;

import com.stockshift.backend.domain.report.ExpiringItemView;
import com.stockshift.backend.domain.report.LowStockView;
import com.stockshift.backend.domain.report.StockHistoryEntry;
import com.stockshift.backend.domain.report.StockSnapshotView;
import com.stockshift.backend.domain.stock.StockEventType;
import com.stockshift.backend.domain.stock.StockReasonCode;
import com.stockshift.backend.domain.stock.exception.StockForbiddenException;
import com.stockshift.backend.domain.stock.exception.StockInvalidPayloadException;
import com.stockshift.backend.domain.user.User;
import com.stockshift.backend.domain.user.UserRole;
import com.stockshift.backend.infrastructure.repository.StockReportRepository;
import com.stockshift.backend.infrastructure.repository.WarehouseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockReportServiceTest {

    @Mock
    private StockReportRepository stockReportRepository;

    @Mock
    private WarehouseRepository warehouseRepository;

    @InjectMocks
    private StockReportService stockReportService;

    private User adminUser;
    private User sellerUser;
    private Pageable pageable;

    @BeforeEach
    void setUp() {
        adminUser = new User();
        adminUser.setId(UUID.randomUUID());
        adminUser.setRole(UserRole.ADMIN);

        sellerUser = new User();
        sellerUser.setId(UUID.randomUUID());
        sellerUser.setRole(UserRole.SELLER);

        pageable = PageRequest.of(0, 20, Sort.by(Sort.Order.desc("quantity")));
    }

    @Test
    void getStockSnapshotShouldReturnPage() {
        UUID warehouseId = UUID.randomUUID();
        when(warehouseRepository.existsById(warehouseId)).thenReturn(true);

        StockSnapshotView view = new StockSnapshotView(
                UUID.randomUUID(),
                "SKU123",
                UUID.randomUUID(),
                "Product",
                null,
                null,
                null,
                null,
                warehouseId,
                "Main Warehouse",
                25L,
                OffsetDateTime.now(ZoneOffset.UTC)
        );
        when(stockReportRepository.findStockSnapshot(any())).thenReturn(List.of(view));

        Page<StockSnapshotView> page = stockReportService.getStockSnapshot(
                warehouseId,
                null,
                null,
                null,
                null,
                null,
                Set.of(),
                false,
                false,
                null,
                pageable,
                adminUser
        );

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).warehouseId()).isEqualTo(warehouseId);
    }

    @Test
    void getStockSnapshotShouldValidateSellerWarehouse() {
        assertThatThrownBy(() -> stockReportService.getStockSnapshot(
                null,
                null,
                null,
                null,
                null,
                null,
                Set.of(),
                false,
                false,
                null,
                pageable,
                sellerUser
        )).isInstanceOf(StockForbiddenException.class);
    }

    @Test
    void getStockHistoryShouldRequireVariantOrProduct() {
        assertThatThrownBy(() -> stockReportService.getStockHistory(
                null,
                null,
                null,
                null,
                null,
                Set.of(),
                pageable,
                adminUser
        )).isInstanceOf(StockInvalidPayloadException.class);
    }

    @Test
    void getStockHistoryShouldReturnSortedPage() {
        UUID variantId = UUID.randomUUID();
        StockHistoryEntry entry = new StockHistoryEntry(
                UUID.randomUUID(),
                StockEventType.INBOUND,
                UUID.randomUUID(),
                "WH",
                OffsetDateTime.now(ZoneOffset.UTC),
                10L,
                5L,
                15L,
                StockReasonCode.PURCHASE,
                null
        );
        when(stockReportRepository.findStockHistory(any(), anyLong())).thenReturn(List.of(entry));

        Page<StockHistoryEntry> page = stockReportService.getStockHistory(
                variantId,
                null,
                null,
                null,
                null,
                Set.of(),
                PageRequest.of(0, 20, Sort.by("occurredAt")),
                adminUser
        );

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).quantityChange()).isEqualTo(10L);
    }

    @Test
    void getLowStockShouldValidateThreshold() {
        assertThatThrownBy(() -> stockReportService.getLowStock(
                null,
                null,
                null,
                null,
                null,
                Set.of(),
                null,
                pageable,
                adminUser
        )).isInstanceOf(StockInvalidPayloadException.class);
    }

    @Test
    void getLowStockShouldReturnItems() {
        when(stockReportRepository.findLowStock(any())).thenReturn(List.of(
                new LowStockView(
                        UUID.randomUUID(),
                        "SKU-1",
                        UUID.randomUUID(),
                        "Prod",
                        null,
                        null,
                        null,
                        null,
                        UUID.randomUUID(),
                        "WH",
                        5L,
                        10L,
                        -5L
                )));

        Page<LowStockView> page = stockReportService.getLowStock(
                null,
                null,
                null,
                null,
                null,
                Set.of(),
                10L,
                pageable,
                adminUser
        );

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).deficit()).isEqualTo(-5L);
    }

    @Test
    void getExpiringItemsShouldDefaultDaysAheadAndReturnItems() {
        when(stockReportRepository.findExpiringItems(any())).thenReturn(List.of(
                new ExpiringItemView(
                        UUID.randomUUID(),
                        "SKU-EXP",
                        UUID.randomUUID(),
                        "Product",
                        null,
                        null,
                        null,
                        null,
                        UUID.randomUUID(),
                        "Warehouse",
                        15L,
                        java.time.LocalDate.now().plusDays(10),
                        10L
                )));

        Page<ExpiringItemView> page = stockReportService.getExpiringItems(
                null,
                null,
                null,
                null,
                null,
                Set.of(),
                null,
                false,
                false,
                null,
                pageable,
                adminUser
        );

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).daysUntilExpiry()).isEqualTo(10L);
    }
}
