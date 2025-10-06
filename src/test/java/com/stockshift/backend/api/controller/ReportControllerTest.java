package com.stockshift.backend.api.controller;

import com.stockshift.backend.api.dto.report.ExpiringItemResponse;
import com.stockshift.backend.api.dto.report.LowStockItemResponse;
import com.stockshift.backend.api.dto.report.StockHistoryEntryResponse;
import com.stockshift.backend.api.dto.report.StockSnapshotItemResponse;
import com.stockshift.backend.api.mapper.ReportMapper;
import com.stockshift.backend.application.service.StockReportService;
import com.stockshift.backend.domain.report.ExpiringItemView;
import com.stockshift.backend.domain.report.LowStockView;
import com.stockshift.backend.domain.report.StockHistoryEntry;
import com.stockshift.backend.domain.report.StockSnapshotView;
import com.stockshift.backend.domain.stock.StockEventType;
import com.stockshift.backend.domain.stock.StockReasonCode;
import com.stockshift.backend.domain.user.User;
import com.stockshift.backend.domain.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportControllerTest {

    @Mock
    private StockReportService stockReportService;

    @Mock
    private ReportMapper reportMapper;

    @InjectMocks
    private ReportController reportController;

    private Authentication authentication;
    private User currentUser;

    @BeforeEach
    void setUp() {
        currentUser = new User();
        currentUser.setId(UUID.randomUUID());
        currentUser.setRole(UserRole.ADMIN);
        currentUser.setActive(true);

        authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(currentUser);
    }

    @Test
    void getStockSnapshotShouldReturnMappedPage() {
        StockSnapshotView view = new StockSnapshotView(
                UUID.randomUUID(),
                "SKU",
                UUID.randomUUID(),
                "Product",
                null,
                null,
                null,
                null,
                UUID.randomUUID(),
                "Warehouse",
                50L,
                OffsetDateTime.now(ZoneOffset.UTC)
        );
        Page<StockSnapshotView> page = new PageImpl<>(List.of(view));
        when(stockReportService.getStockSnapshot(
                any(), any(), any(), any(), any(), nullable(String.class), anySet(), anyBoolean(), anyBoolean(), any(), any(Pageable.class), eq(currentUser)
        )).thenReturn(page);

        StockSnapshotItemResponse mapped = StockSnapshotItemResponse.builder()
                .variantId(view.variantId())
                .sku(view.sku())
                .quantity(view.quantity())
                .build();
        when(reportMapper.toResponse(view)).thenReturn(mapped);

        Pageable pageable = PageRequest.of(0, 20);
        ResponseEntity<Page<StockSnapshotItemResponse>> response = reportController.getStockSnapshot(
                null, null, null, null, null, null, null, false, false, null, pageable, authentication
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getContent()).containsExactly(mapped);
    }

    @Test
    void getStockHistoryShouldReturnMappedPage() {
        StockHistoryEntry entry = new StockHistoryEntry(
                UUID.randomUUID(),
                StockEventType.INBOUND,
                UUID.randomUUID(),
                "Warehouse",
                OffsetDateTime.now(ZoneOffset.UTC),
                10L,
                0L,
                10L,
                StockReasonCode.PURCHASE,
                null
        );
        Page<StockHistoryEntry> page = new PageImpl<>(List.of(entry));
        when(stockReportService.getStockHistory(
                any(), any(), any(), any(), any(), anySet(), any(Pageable.class), eq(currentUser)
        )).thenReturn(page);

        StockHistoryEntryResponse mapped = StockHistoryEntryResponse.builder()
                .eventId(entry.eventId())
                .quantityChange(entry.quantityChange())
                .build();
        when(reportMapper.toResponse(entry)).thenReturn(mapped);

        Pageable pageable = PageRequest.of(0, 20);
        ResponseEntity<Page<StockHistoryEntryResponse>> response = reportController.getStockHistory(
                UUID.randomUUID(), null, null, null, null, null, pageable, authentication
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getContent()).containsExactly(mapped);
    }

    @Test
    void getLowStockShouldReturnMappedPage() {
        LowStockView view = new LowStockView(
                UUID.randomUUID(),
                "SKU",
                UUID.randomUUID(),
                "Product",
                null,
                null,
                null,
                null,
                UUID.randomUUID(),
                "Warehouse",
                5L,
                10L,
                -5L
        );
        Page<LowStockView> page = new PageImpl<>(List.of(view));
        when(stockReportService.getLowStock(
                any(), any(), any(), any(), nullable(String.class), anySet(), any(), any(Pageable.class), eq(currentUser)
        )).thenReturn(page);

        LowStockItemResponse mapped = LowStockItemResponse.builder()
                .variantId(view.variantId())
                .quantity(view.quantity())
                .threshold(view.threshold())
                .deficit(view.deficit())
                .build();
        when(reportMapper.toResponse(view)).thenReturn(mapped);

        Pageable pageable = PageRequest.of(0, 20);
        ResponseEntity<Page<LowStockItemResponse>> response = reportController.getLowStock(
                null, null, null, null, null, null, 5L, pageable, authentication
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getContent()).containsExactly(mapped);
    }

    @Test
    void getExpiringItemsShouldReturnMappedPage() {
        ExpiringItemView view = new ExpiringItemView(
                UUID.randomUUID(),
                "SKU",
                UUID.randomUUID(),
                "Product",
                null,
                null,
                null,
                null,
                UUID.randomUUID(),
                "Warehouse",
                30L,
                LocalDate.now().plusDays(5),
                5L
        );
        Page<ExpiringItemView> page = new PageImpl<>(List.of(view));
        when(stockReportService.getExpiringItems(
                any(), any(), any(), any(), nullable(String.class), anySet(), anyInt(), anyBoolean(), anyBoolean(), any(), any(Pageable.class), eq(currentUser)
        )).thenReturn(page);

        ExpiringItemResponse mapped = ExpiringItemResponse.builder()
                .variantId(view.variantId())
                .quantity(view.quantity())
                .daysUntilExpiry(view.daysUntilExpiry())
                .build();
        when(reportMapper.toResponse(view)).thenReturn(mapped);

        Pageable pageable = PageRequest.of(0, 20);
        ResponseEntity<Page<ExpiringItemResponse>> response = reportController.getExpiringItems(
                null, null, null, null, null, null, 10, false, false, OffsetDateTime.now(ZoneOffset.UTC), pageable, authentication
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getContent()).containsExactly(mapped);
    }
}
