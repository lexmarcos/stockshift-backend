package com.stockshift.backend.application.service;

import com.stockshift.backend.domain.report.ExpiringItemFilter;
import com.stockshift.backend.domain.report.ExpiringItemView;
import com.stockshift.backend.domain.report.LowStockFilter;
import com.stockshift.backend.domain.report.LowStockView;
import com.stockshift.backend.domain.report.StockHistoryEntry;
import com.stockshift.backend.domain.report.StockHistoryFilter;
import com.stockshift.backend.domain.report.StockSnapshotFilter;
import com.stockshift.backend.domain.report.StockSnapshotView;
import com.stockshift.backend.domain.stock.exception.StockForbiddenException;
import com.stockshift.backend.domain.stock.exception.StockInvalidPayloadException;
import com.stockshift.backend.domain.user.User;
import com.stockshift.backend.domain.user.UserRole;
import com.stockshift.backend.infrastructure.repository.StockReportRepository;
import com.stockshift.backend.infrastructure.repository.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StockReportService {

    private final StockReportRepository stockReportRepository;
    private final WarehouseRepository warehouseRepository;

    @Transactional(readOnly = true)
    public Page<StockSnapshotView> getStockSnapshot(
            UUID warehouseId,
            UUID productId,
            UUID categoryId,
            UUID brandId,
            UUID variantId,
            String sku,
            Set<UUID> attributeValueIds,
            boolean aggregateByWarehouse,
            boolean includeZero,
            OffsetDateTime asOf,
            Pageable pageable,
            User currentUser
    ) {
        User user = requireAuthenticatedUser(currentUser);

        if (user.getRole() == UserRole.SELLER && warehouseId == null) {
            throw new StockForbiddenException();
        }

        if (warehouseId != null && !warehouseRepository.existsById(warehouseId)) {
            throw new StockInvalidPayloadException("warehouse-not-found");
        }

        OffsetDateTime normalizedAsOf = asOf != null ? asOf.withOffsetSameInstant(ZoneOffset.UTC) : null;

        StockSnapshotFilter filter = new StockSnapshotFilter(
                warehouseId,
                productId,
                categoryId,
                brandId,
                variantId,
                sku,
                attributeValueIds,
                aggregateByWarehouse,
                includeZero,
                normalizedAsOf
        );

        List<StockSnapshotView> records = stockReportRepository.findStockSnapshot(filter);

        List<StockSnapshotView> sorted = sortSnapshot(records, pageable.getSort());

        return createPage(sorted, pageable);
    }

    @Transactional(readOnly = true)
    public Page<StockHistoryEntry> getStockHistory(
            UUID variantId,
            UUID productId,
            UUID warehouseId,
            OffsetDateTime dateFrom,
            OffsetDateTime dateTo,
            Set<UUID> attributeValueIds,
            Pageable pageable,
            User currentUser
    ) {
        User user = requireAuthenticatedUser(currentUser);
        if (variantId == null && productId == null) {
            throw new StockInvalidPayloadException("invalid-filters: variant-or-product-required");
        }

        if (user.getRole() == UserRole.SELLER && warehouseId == null) {
            throw new StockForbiddenException();
        }

        if (warehouseId != null && !warehouseRepository.existsById(warehouseId)) {
            throw new StockInvalidPayloadException("warehouse-not-found");
        }

        OffsetDateTime normalizedFrom = dateFrom != null ? dateFrom.withOffsetSameInstant(ZoneOffset.UTC) : null;
        OffsetDateTime normalizedTo = dateTo != null ? dateTo.withOffsetSameInstant(ZoneOffset.UTC) : null;

        if (normalizedFrom != null && normalizedTo != null && normalizedFrom.isAfter(normalizedTo)) {
            throw new StockInvalidPayloadException("invalid-range");
        }

        StockHistoryFilter filter = new StockHistoryFilter(
                variantId,
                productId,
                warehouseId,
                normalizedFrom,
                normalizedTo,
                attributeValueIds
        );

        long startingBalance = normalizedFrom != null
                ? stockReportRepository.calculateBalanceBefore(filter, normalizedFrom)
                : 0L;

        List<StockHistoryEntry> entries = stockReportRepository.findStockHistory(filter, startingBalance);

        List<StockHistoryEntry> sorted = sortHistory(entries, pageable.getSort());

        return createPage(sorted, pageable);
    }

    @Transactional(readOnly = true)
    public Page<LowStockView> getLowStock(
            UUID warehouseId,
            UUID productId,
            UUID categoryId,
            UUID brandId,
            String sku,
            Set<UUID> attributeValueIds,
            Long threshold,
            Pageable pageable,
            User currentUser
    ) {
        User user = requireAuthenticatedUser(currentUser);

        if (threshold == null || threshold <= 0) {
            throw new StockInvalidPayloadException("threshold-required");
        }

        if (user.getRole() == UserRole.SELLER && warehouseId == null) {
            throw new StockForbiddenException();
        }

        if (warehouseId != null && !warehouseRepository.existsById(warehouseId)) {
            throw new StockInvalidPayloadException("warehouse-not-found");
        }

        LowStockFilter filter = new LowStockFilter(
                warehouseId,
                productId,
                categoryId,
                brandId,
                sku,
                attributeValueIds,
                threshold
        );

        List<LowStockView> items = stockReportRepository.findLowStock(filter);

        List<LowStockView> sorted = sortLowStock(items, pageable.getSort());

        return createPage(sorted, pageable);
    }

    @Transactional(readOnly = true)
    public Page<ExpiringItemView> getExpiringItems(
            UUID warehouseId,
            UUID productId,
            UUID categoryId,
            UUID brandId,
            String sku,
            Set<UUID> attributeValueIds,
            Integer daysAhead,
            boolean includeExpired,
            boolean aggregateByWarehouse,
            OffsetDateTime asOf,
            Pageable pageable,
            User currentUser
    ) {
        User user = requireAuthenticatedUser(currentUser);

        if (user.getRole() == UserRole.SELLER && warehouseId == null) {
            throw new StockForbiddenException();
        }

        if (warehouseId != null && !warehouseRepository.existsById(warehouseId)) {
            throw new StockInvalidPayloadException("warehouse-not-found");
        }

        int effectiveDaysAhead = daysAhead == null || daysAhead <= 0 ? 30 : daysAhead;
        OffsetDateTime normalized = asOf != null ? asOf.withOffsetSameInstant(ZoneOffset.UTC) : OffsetDateTime.now(ZoneOffset.UTC);
        LocalDate asOfDate = normalized.toLocalDate();

        ExpiringItemFilter filter = new ExpiringItemFilter(
                warehouseId,
                productId,
                categoryId,
                brandId,
                sku,
                attributeValueIds,
                asOfDate,
                effectiveDaysAhead,
                includeExpired,
                aggregateByWarehouse
        );

        List<ExpiringItemView> items = stockReportRepository.findExpiringItems(filter);

        List<ExpiringItemView> sorted = sortExpiring(items, pageable.getSort());

        return createPage(sorted, pageable);
    }

    private User requireAuthenticatedUser(User currentUser) {
        if (currentUser == null || currentUser.getRole() == null) {
            throw new StockForbiddenException();
        }
        return currentUser;
    }

    private List<StockSnapshotView> sortSnapshot(List<StockSnapshotView> input, Sort sort) {
        if (input.isEmpty()) {
            return input;
        }
        if (sort == null || sort.isUnsorted()) {
            return StockReportRepository.sortSnapshotViews(input, "quantity", false);
        }
        List<Sort.Order> orders = new ArrayList<>();
        sort.forEach(orders::add);
        Collections.reverse(orders);
        List<StockSnapshotView> sorted = input;
        for (Sort.Order order : orders) {
            sorted = StockReportRepository.sortSnapshotViews(sorted, order.getProperty(), order.isAscending());
        }
        return sorted;
    }

    private List<StockHistoryEntry> sortHistory(List<StockHistoryEntry> input, Sort sort) {
        if (input.isEmpty() || sort == null || sort.isUnsorted()) {
            return input;
        }
        List<Sort.Order> orders = new ArrayList<>();
        sort.forEach(orders::add);
        Collections.reverse(orders);
        List<StockHistoryEntry> result = input;
        for (Sort.Order order : orders) {
            if ("occurredAt".equals(order.getProperty())) {
                result = result.stream()
                        .sorted(order.isAscending()
                                ? java.util.Comparator.comparing(StockHistoryEntry::occurredAt)
                                : java.util.Comparator.comparing(StockHistoryEntry::occurredAt).reversed())
                        .toList();
            }
        }
        return result;
    }

    private List<LowStockView> sortLowStock(List<LowStockView> input, Sort sort) {
        if (input.isEmpty()) {
            return input;
        }
        if (sort == null || sort.isUnsorted()) {
            return StockReportRepository.sortLowStockViews(input, "deficit", true);
        }
        List<Sort.Order> orders = new ArrayList<>();
        sort.forEach(orders::add);
        Collections.reverse(orders);
        List<LowStockView> sorted = input;
        for (Sort.Order order : orders) {
            sorted = StockReportRepository.sortLowStockViews(sorted, order.getProperty(), order.isAscending());
        }
        return sorted;
    }

    private List<ExpiringItemView> sortExpiring(List<ExpiringItemView> input, Sort sort) {
        if (input.isEmpty()) {
            return input;
        }
        if (sort == null || sort.isUnsorted()) {
            return StockReportRepository.sortExpiringViews(input, "expiryDate", true);
        }
        List<Sort.Order> orders = new ArrayList<>();
        sort.forEach(orders::add);
        Collections.reverse(orders);
        List<ExpiringItemView> sorted = input;
        for (Sort.Order order : orders) {
            sorted = StockReportRepository.sortExpiringViews(sorted, order.getProperty(), order.isAscending());
        }
        return sorted;
    }

    private <T> Page<T> createPage(List<T> sorted, Pageable pageable) {
        if (pageable == null || pageable.isUnpaged()) {
            return new PageImpl<>(sorted);
        }
        List<T> pageContent = StockReportRepository.paginate(sorted, pageable.getPageNumber(), pageable.getPageSize());
        return new PageImpl<>(pageContent, pageable, sorted.size());
    }
}
