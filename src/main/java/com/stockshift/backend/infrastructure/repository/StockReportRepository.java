package com.stockshift.backend.infrastructure.repository;

import com.stockshift.backend.domain.product.Product;
import com.stockshift.backend.domain.product.ProductVariant;
import com.stockshift.backend.domain.product.ProductVariantAttribute;
import com.stockshift.backend.domain.report.ExpiringItemFilter;
import com.stockshift.backend.domain.report.ExpiringItemView;
import com.stockshift.backend.domain.report.LowStockFilter;
import com.stockshift.backend.domain.report.LowStockView;
import com.stockshift.backend.domain.report.StockHistoryFilter;
import com.stockshift.backend.domain.report.StockHistoryEntry;
import com.stockshift.backend.domain.report.StockSnapshotFilter;
import com.stockshift.backend.domain.report.StockSnapshotView;
import com.stockshift.backend.domain.stock.StockEvent;
import com.stockshift.backend.domain.stock.StockEventLine;
import com.stockshift.backend.domain.stock.StockEventType;
import com.stockshift.backend.domain.stock.StockReasonCode;
import com.stockshift.backend.domain.stock.StockItem;
import com.stockshift.backend.domain.warehouse.Warehouse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class StockReportRepository {

    private final EntityManager entityManager;

    public List<StockSnapshotView> findStockSnapshot(StockSnapshotFilter filter) {
        if (filter.asOf() != null) {
            return findSnapshotAsOf(filter);
        }
        if (filter.aggregateByWarehouse()) {
            return findSnapshotAggregated(filter);
        }
        return findSnapshotPerWarehouse(filter);
    }

    private List<StockSnapshotView> findSnapshotPerWarehouse(StockSnapshotFilter filter) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> query = cb.createTupleQuery();
        Root<StockItem> stock = query.from(StockItem.class);
        Join<StockItem, Warehouse> warehouse = stock.join("warehouse", JoinType.INNER);
        Join<StockItem, ProductVariant> variant = stock.join("variant", JoinType.INNER);
        Join<ProductVariant, Product> product = variant.join("product", JoinType.INNER);
        Join<Product, ?> brand = product.join("brand", JoinType.LEFT);
        Join<Product, ?> category = product.join("category", JoinType.LEFT);
        Join<ProductVariant, ProductVariantAttribute> attrJoin = null;

        if (!filter.attributeValueIds().isEmpty()) {
            attrJoin = variant.join("attributes", JoinType.INNER);
        }

        List<Predicate> predicates = buildCommonPredicates(
                filter,
                cb,
                stock,
                warehouse,
                variant,
                product,
                brand,
                category,
                attrJoin
        );

        if (!filter.includeZero()) {
            predicates.add(cb.notEqual(stock.get("quantity"), 0L));
        }

        query.multiselect(
                variant.get("id"),
                variant.get("sku"),
                product.get("id"),
                product.get("name"),
                brand.get("id"),
                brand.get("name"),
                category.get("id"),
                category.get("name"),
                warehouse.get("id"),
                warehouse.get("name"),
                stock.get("quantity")
        );

        if (!predicates.isEmpty()) {
            query.where(predicates.toArray(Predicate[]::new));
        }
        query.distinct(true);

        List<Tuple> tuples = entityManager.createQuery(query).getResultList();
        OffsetDateTime asOf = OffsetDateTime.now(ZoneOffset.UTC);
        return tuples.stream()
                .map(tuple -> new StockSnapshotView(
                        tuple.get(0, UUID.class),
                        tuple.get(1, String.class),
                        tuple.get(2, UUID.class),
                        tuple.get(3, String.class),
                        tuple.get(4, UUID.class),
                        tuple.get(5, String.class),
                        tuple.get(6, UUID.class),
                        tuple.get(7, String.class),
                        tuple.get(8, UUID.class),
                        tuple.get(9, String.class),
                        tuple.get(10, Number.class).longValue(),
                        asOf
                ))
                .toList();
    }

    private List<StockSnapshotView> findSnapshotAggregated(StockSnapshotFilter filter) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> query = cb.createTupleQuery();
        Root<StockItem> stock = query.from(StockItem.class);
        Join<StockItem, ProductVariant> variant = stock.join("variant", JoinType.INNER);
        Join<ProductVariant, Product> product = variant.join("product", JoinType.INNER);
        Join<Product, ?> brand = product.join("brand", JoinType.LEFT);
        Join<Product, ?> category = product.join("category", JoinType.LEFT);
        Join<ProductVariant, ProductVariantAttribute> attrJoin = null;

        if (!filter.attributeValueIds().isEmpty()) {
            attrJoin = variant.join("attributes", JoinType.INNER);
        }

        List<Predicate> predicates = buildCommonPredicates(
                filter,
                cb,
                stock,
                null,
                variant,
                product,
                brand,
                category,
                attrJoin
        );

        Expression<Long> sumQuantity = cb.sum(stock.get("quantity"));

        query.multiselect(
                variant.get("id"),
                variant.get("sku"),
                product.get("id"),
                product.get("name"),
                brand.get("id"),
                brand.get("name"),
                category.get("id"),
                category.get("name"),
                cb.nullLiteral(UUID.class),
                cb.nullLiteral(String.class),
                sumQuantity
        );

        if (!predicates.isEmpty()) {
            query.where(predicates.toArray(Predicate[]::new));
        }

        query.groupBy(
                variant.get("id"),
                variant.get("sku"),
                product.get("id"),
                product.get("name"),
                brand.get("id"),
                brand.get("name"),
                category.get("id"),
                category.get("name")
        );

        if (!filter.includeZero()) {
            query.having(cb.notEqual(sumQuantity, 0L));
        }

        List<Tuple> tuples = entityManager.createQuery(query).getResultList();
        OffsetDateTime asOf = OffsetDateTime.now(ZoneOffset.UTC);
        return tuples.stream()
                .map(tuple -> new StockSnapshotView(
                        tuple.get(0, UUID.class),
                        tuple.get(1, String.class),
                        tuple.get(2, UUID.class),
                        tuple.get(3, String.class),
                        tuple.get(4, UUID.class),
                        tuple.get(5, String.class),
                        tuple.get(6, UUID.class),
                        tuple.get(7, String.class),
                        null,
                        null,
                        tuple.get(10, Number.class).longValue(),
                        asOf
                ))
                .toList();
    }

    private List<StockSnapshotView> findSnapshotAsOf(StockSnapshotFilter filter) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> query = cb.createTupleQuery();
        Root<StockEventLine> line = query.from(StockEventLine.class);
        Join<StockEventLine, StockEvent> event = line.join("event", JoinType.INNER);
        Join<StockEventLine, ProductVariant> variant = line.join("variant", JoinType.INNER);
        Join<ProductVariant, Product> product = variant.join("product", JoinType.INNER);
        Join<Product, ?> brand = product.join("brand", JoinType.LEFT);
        Join<Product, ?> category = product.join("category", JoinType.LEFT);
        Join<StockEvent, Warehouse> warehouse = filter.aggregateByWarehouse()
                ? null
                : event.join("warehouse", JoinType.INNER);

        Join<ProductVariant, ProductVariantAttribute> attrJoin = null;
        if (!filter.attributeValueIds().isEmpty()) {
            attrJoin = variant.join("attributes", JoinType.INNER);
        }

        List<Predicate> predicates = new ArrayList<>();
        predicates.add(cb.lessThanOrEqualTo(event.get("occurredAt"), filter.asOf()));

        if (filter.warehouseId() != null) {
            predicates.add(cb.equal(event.get("warehouse").get("id"), filter.warehouseId()));
        }
        if (filter.productId() != null) {
            predicates.add(cb.equal(product.get("id"), filter.productId()));
        }
        if (filter.categoryId() != null) {
            predicates.add(cb.equal(product.get("category").get("id"), filter.categoryId()));
        }
        if (filter.brandId() != null) {
            predicates.add(cb.equal(product.get("brand").get("id"), filter.brandId()));
        }
        if (filter.variantId() != null) {
            predicates.add(cb.equal(variant.get("id"), filter.variantId()));
        }
        if (filter.sku() != null && !filter.sku().isBlank()) {
            predicates.add(cb.like(cb.lower(variant.get("sku")), likePattern(filter.sku())));
        }
        if (!filter.attributeValueIds().isEmpty() && attrJoin != null) {
            predicates.add(attrJoin.get("value").get("id").in(filter.attributeValueIds()));
        }

        Expression<Long> sumQuantity = cb.sum(line.get("quantity"));

        query.multiselect(
                variant.get("id"),
                variant.get("sku"),
                product.get("id"),
                product.get("name"),
                brand.get("id"),
                brand.get("name"),
                category.get("id"),
                category.get("name"),
                filter.aggregateByWarehouse() ? cb.nullLiteral(UUID.class) : event.get("warehouse").get("id"),
                filter.aggregateByWarehouse() ? cb.nullLiteral(String.class) : event.get("warehouse").get("name"),
                sumQuantity
        );

        if (!predicates.isEmpty()) {
            query.where(predicates.toArray(Predicate[]::new));
        }

        if (filter.aggregateByWarehouse()) {
            query.groupBy(
                    variant.get("id"),
                    variant.get("sku"),
                    product.get("id"),
                    product.get("name"),
                    brand.get("id"),
                    brand.get("name"),
                    category.get("id"),
                    category.get("name")
            );
        } else {
            query.groupBy(
                    variant.get("id"),
                    variant.get("sku"),
                    product.get("id"),
                    product.get("name"),
                    brand.get("id"),
                    brand.get("name"),
                    category.get("id"),
                    category.get("name"),
                    event.get("warehouse").get("id"),
                    event.get("warehouse").get("name")
            );
        }

        if (!filter.includeZero()) {
            query.having(cb.notEqual(sumQuantity, 0L));
        }

        List<Tuple> tuples = entityManager.createQuery(query).getResultList();
        OffsetDateTime asOf = filter.asOf();
        return tuples.stream()
                .map(tuple -> new StockSnapshotView(
                        tuple.get(0, UUID.class),
                        tuple.get(1, String.class),
                        tuple.get(2, UUID.class),
                        tuple.get(3, String.class),
                        tuple.get(4, UUID.class),
                        tuple.get(5, String.class),
                        tuple.get(6, UUID.class),
                        tuple.get(7, String.class),
                        tuple.get(8, UUID.class),
                        tuple.get(9, String.class),
                        tuple.get(10, Number.class).longValue(),
                        asOf
                ))
                .toList();
    }

    public List<StockHistoryEntry> findStockHistory(StockHistoryFilter filter, long startingBalance) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> query = cb.createTupleQuery();
        Root<StockEventLine> line = query.from(StockEventLine.class);
        Join<StockEventLine, StockEvent> event = line.join("event", JoinType.INNER);
        Join<StockEventLine, ProductVariant> variant = line.join("variant", JoinType.INNER);
        Join<StockEvent, Warehouse> warehouse = event.join("warehouse", JoinType.INNER);
        Join<ProductVariant, ProductVariantAttribute> attrJoin = null;
        if (!filter.attributeValueIds().isEmpty()) {
            attrJoin = variant.join("attributes", JoinType.INNER);
        }

        List<Predicate> predicates = new ArrayList<>();
        if (filter.variantId() != null) {
            predicates.add(cb.equal(variant.get("id"), filter.variantId()));
        }
        if (filter.productId() != null) {
            predicates.add(cb.equal(variant.get("product").get("id"), filter.productId()));
        }
        if (filter.warehouseId() != null) {
            predicates.add(cb.equal(warehouse.get("id"), filter.warehouseId()));
        }
        if (filter.dateFrom() != null) {
            predicates.add(cb.greaterThanOrEqualTo(event.get("occurredAt"), filter.dateFrom()));
        }
        if (filter.dateTo() != null) {
            predicates.add(cb.lessThanOrEqualTo(event.get("occurredAt"), filter.dateTo()));
        }
        if (!filter.attributeValueIds().isEmpty() && attrJoin != null) {
            predicates.add(attrJoin.get("value").get("id").in(filter.attributeValueIds()));
        }

        query.multiselect(
                event.get("id"),
                event.get("type"),
                warehouse.get("id"),
                warehouse.get("name"),
                event.get("occurredAt"),
                line.get("quantity"),
                event.get("reasonCode"),
                event.get("notes")
        );

        if (!predicates.isEmpty()) {
            query.where(predicates.toArray(Predicate[]::new));
        }

        query.orderBy(cb.asc(event.get("occurredAt")), cb.asc(event.get("createdAt")));

        List<Tuple> tuples = entityManager.createQuery(query).getResultList();
        List<StockHistoryEntry> rows = new ArrayList<>(tuples.size());
        long running = startingBalance;
        for (Tuple tuple : tuples) {
            long quantityChange = tuple.get(5, Number.class).longValue();
            long before = running;
            long after = before + quantityChange;
            running = after;
            rows.add(new StockHistoryEntry(
                    tuple.get(0, UUID.class),
                    tuple.get(1, StockEventType.class),
                    tuple.get(2, UUID.class),
                    tuple.get(3, String.class),
                    tuple.get(4, OffsetDateTime.class),
                    quantityChange,
                    before,
                    after,
                    tuple.get(6, StockReasonCode.class),
                    tuple.get(7, String.class)
            ));
        }
        return rows;
    }

    public long calculateBalanceBefore(StockHistoryFilter filter, OffsetDateTime before) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> query = cb.createQuery(Long.class);
        Root<StockEventLine> line = query.from(StockEventLine.class);
        Join<StockEventLine, StockEvent> event = line.join("event", JoinType.INNER);

        List<Predicate> predicates = new ArrayList<>();
        if (filter.variantId() != null) {
            predicates.add(cb.equal(line.get("variant").get("id"), filter.variantId()));
        }
        if (filter.productId() != null) {
            predicates.add(cb.equal(line.get("variant").get("product").get("id"), filter.productId()));
        }
        if (filter.warehouseId() != null) {
            predicates.add(cb.equal(event.get("warehouse").get("id"), filter.warehouseId()));
        }
        if (before != null) {
            predicates.add(cb.lessThan(event.get("occurredAt"), before));
        }
        if (!filter.attributeValueIds().isEmpty()) {
            Join<StockEventLine, ProductVariant> variant = line.join("variant", JoinType.INNER);
            Join<ProductVariant, ProductVariantAttribute> attrJoin = variant.join("attributes", JoinType.INNER);
            predicates.add(attrJoin.get("value").get("id").in(filter.attributeValueIds()));
        }

        query.select(cb.coalesce(cb.sum(line.get("quantity")), 0L));
        if (!predicates.isEmpty()) {
            query.where(predicates.toArray(Predicate[]::new));
        }
        Long result = entityManager.createQuery(query).getSingleResult();
        return result != null ? result : 0L;
    }

    public List<LowStockView> findLowStock(LowStockFilter filter) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> query = cb.createTupleQuery();
        Root<StockItem> stock = query.from(StockItem.class);
        Join<StockItem, Warehouse> warehouse = stock.join("warehouse", JoinType.INNER);
        Join<StockItem, ProductVariant> variant = stock.join("variant", JoinType.INNER);
        Join<ProductVariant, Product> product = variant.join("product", JoinType.INNER);
        Join<Product, ?> brand = product.join("brand", JoinType.LEFT);
        Join<Product, ?> category = product.join("category", JoinType.LEFT);
        Join<ProductVariant, ProductVariantAttribute> attrJoin = null;

        if (!filter.attributeValueIds().isEmpty()) {
            attrJoin = variant.join("attributes", JoinType.INNER);
        }

        List<Predicate> predicates = new ArrayList<>();
        predicates.add(cb.lessThan(stock.get("quantity"), filter.threshold()));

        if (filter.warehouseId() != null) {
            predicates.add(cb.equal(warehouse.get("id"), filter.warehouseId()));
        }
        if (filter.productId() != null) {
            predicates.add(cb.equal(product.get("id"), filter.productId()));
        }
        if (filter.categoryId() != null) {
            predicates.add(cb.equal(product.get("category").get("id"), filter.categoryId()));
        }
        if (filter.brandId() != null) {
            predicates.add(cb.equal(product.get("brand").get("id"), filter.brandId()));
        }
        if (filter.sku() != null && !filter.sku().isBlank()) {
            predicates.add(cb.like(cb.lower(variant.get("sku")), likePattern(filter.sku())));
        }
        if (!filter.attributeValueIds().isEmpty() && attrJoin != null) {
            predicates.add(attrJoin.get("value").get("id").in(filter.attributeValueIds()));
        }

        query.multiselect(
                variant.get("id"),
                variant.get("sku"),
                product.get("id"),
                product.get("name"),
                brand.get("id"),
                brand.get("name"),
                category.get("id"),
                category.get("name"),
                warehouse.get("id"),
                warehouse.get("name"),
                stock.get("quantity")
        );

        query.where(predicates.toArray(Predicate[]::new));
        query.distinct(true);

        List<Tuple> tuples = entityManager.createQuery(query).getResultList();
        return tuples.stream()
                .map(tuple -> {
                    long quantity = tuple.get(10, Number.class).longValue();
                    long threshold = filter.threshold();
                    long deficit = quantity - threshold;
                    return new LowStockView(
                            tuple.get(0, UUID.class),
                            tuple.get(1, String.class),
                            tuple.get(2, UUID.class),
                            tuple.get(3, String.class),
                            tuple.get(4, UUID.class),
                            tuple.get(5, String.class),
                            tuple.get(6, UUID.class),
                            tuple.get(7, String.class),
                            tuple.get(8, UUID.class),
                            tuple.get(9, String.class),
                            quantity,
                            threshold,
                            deficit
                    );
                })
                .toList();
    }

    public List<ExpiringItemView> findExpiringItems(ExpiringItemFilter filter) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> query = cb.createTupleQuery();
        Root<StockItem> stock = query.from(StockItem.class);
        Join<StockItem, Warehouse> warehouse = filter.aggregateByWarehouse() ? null : stock.join("warehouse", JoinType.INNER);
        Join<StockItem, ProductVariant> variant = stock.join("variant", JoinType.INNER);
        Join<ProductVariant, Product> product = variant.join("product", JoinType.INNER);
        Join<Product, ?> brand = product.join("brand", JoinType.LEFT);
        Join<Product, ?> category = product.join("category", JoinType.LEFT);
        Join<ProductVariant, ProductVariantAttribute> attrJoin = null;

        if (!filter.attributeValueIds().isEmpty()) {
            attrJoin = variant.join("attributes", JoinType.INNER);
        }

        LocalDate asOfDate = filter.asOfDate();
        LocalDate endDate = asOfDate.plusDays(filter.daysAhead());

        List<Predicate> predicates = new ArrayList<>();
        predicates.add(cb.isNotNull(product.get("expiryDate")));
        predicates.add(cb.lessThanOrEqualTo(product.get("expiryDate"), endDate));
        if (!filter.includeExpired()) {
            predicates.add(cb.greaterThanOrEqualTo(product.get("expiryDate"), asOfDate));
        }

        if (filter.warehouseId() != null) {
            predicates.add(cb.equal(stock.get("warehouse").get("id"), filter.warehouseId()));
        }
        if (filter.productId() != null) {
            predicates.add(cb.equal(product.get("id"), filter.productId()));
        }
        if (filter.categoryId() != null) {
            predicates.add(cb.equal(product.get("category").get("id"), filter.categoryId()));
        }
        if (filter.brandId() != null) {
            predicates.add(cb.equal(product.get("brand").get("id"), filter.brandId()));
        }
        if (filter.sku() != null && !filter.sku().isBlank()) {
            predicates.add(cb.like(cb.lower(variant.get("sku")), likePattern(filter.sku())));
        }
        if (!filter.attributeValueIds().isEmpty() && attrJoin != null) {
            predicates.add(attrJoin.get("value").get("id").in(filter.attributeValueIds()));
        }

        Expression<Long> quantityExpression = filter.aggregateByWarehouse()
                ? cb.sum(stock.get("quantity"))
                : stock.get("quantity");

        query.multiselect(
                variant.get("id"),
                variant.get("sku"),
                product.get("id"),
                product.get("name"),
                brand.get("id"),
                brand.get("name"),
                category.get("id"),
                category.get("name"),
                filter.aggregateByWarehouse() ? cb.nullLiteral(UUID.class) : stock.get("warehouse").get("id"),
                filter.aggregateByWarehouse() ? cb.nullLiteral(String.class) : stock.get("warehouse").get("name"),
                quantityExpression,
                product.get("expiryDate")
        );

        if (!predicates.isEmpty()) {
            query.where(predicates.toArray(Predicate[]::new));
        }

        if (filter.aggregateByWarehouse()) {
            query.groupBy(
                    variant.get("id"),
                    variant.get("sku"),
                    product.get("id"),
                    product.get("name"),
                    brand.get("id"),
                    brand.get("name"),
                    category.get("id"),
                    category.get("name"),
                    product.get("expiryDate")
            );
        } else {
            query.groupBy(
                    variant.get("id"),
                    variant.get("sku"),
                    product.get("id"),
                    product.get("name"),
                    brand.get("id"),
                    brand.get("name"),
                    category.get("id"),
                    category.get("name"),
                    stock.get("warehouse").get("id"),
                    stock.get("warehouse").get("name"),
                    product.get("expiryDate")
            );
        }

        List<Tuple> tuples = entityManager.createQuery(query).getResultList();

        return tuples.stream()
                .map(tuple -> {
                    long quantity = tuple.get(10, Number.class).longValue();
                    LocalDate expiryDate = tuple.get(11, LocalDate.class);
                    long days = java.time.temporal.ChronoUnit.DAYS.between(asOfDate, expiryDate);
                    return new ExpiringItemView(
                            tuple.get(0, UUID.class),
                            tuple.get(1, String.class),
                            tuple.get(2, UUID.class),
                            tuple.get(3, String.class),
                            tuple.get(4, UUID.class),
                            tuple.get(5, String.class),
                            tuple.get(6, UUID.class),
                            tuple.get(7, String.class),
                            tuple.get(8, UUID.class),
                            tuple.get(9, String.class),
                            quantity,
                            expiryDate,
                            days
                    );
                })
                .toList();
    }

    private List<Predicate> buildCommonPredicates(
            StockSnapshotFilter filter,
            CriteriaBuilder cb,
            Root<StockItem> stock,
            Join<StockItem, Warehouse> warehouse,
            Join<?, ProductVariant> variant,
            Join<ProductVariant, Product> product,
            Join<Product, ?> brand,
            Join<Product, ?> category,
            Join<ProductVariant, ProductVariantAttribute> attrJoin
    ) {
        List<Predicate> predicates = new ArrayList<>();
        if (filter.warehouseId() != null) {
            if (warehouse != null) {
                predicates.add(cb.equal(warehouse.get("id"), filter.warehouseId()));
            } else {
                predicates.add(cb.equal(stock.get("warehouse").get("id"), filter.warehouseId()));
            }
        }
        if (filter.productId() != null) {
            predicates.add(cb.equal(product.get("id"), filter.productId()));
        }
        if (filter.categoryId() != null) {
            predicates.add(cb.equal(product.get("category").get("id"), filter.categoryId()));
        }
        if (filter.brandId() != null) {
            predicates.add(cb.equal(product.get("brand").get("id"), filter.brandId()));
        }
        if (filter.variantId() != null) {
            predicates.add(cb.equal(variant.get("id"), filter.variantId()));
        }
        if (filter.sku() != null && !filter.sku().isBlank()) {
            predicates.add(cb.like(cb.lower(variant.get("sku")), likePattern(filter.sku())));
        }
        if (!filter.attributeValueIds().isEmpty() && attrJoin != null) {
            predicates.add(attrJoin.get("value").get("id").in(filter.attributeValueIds()));
        }
        return predicates;
    }

    public static List<StockSnapshotView> sortSnapshotViews(List<StockSnapshotView> input, String property, boolean ascending) {
        Comparator<StockSnapshotView> comparator = switch (property) {
            case "sku" -> Comparator.comparing(StockSnapshotView::sku, Comparator.nullsLast(String::compareToIgnoreCase));
            case "productName" -> Comparator.comparing(StockSnapshotView::productName, Comparator.nullsLast(String::compareToIgnoreCase));
            case "brandName" -> Comparator.comparing(StockSnapshotView::brandName, Comparator.nullsLast(String::compareToIgnoreCase));
            case "categoryName" -> Comparator.comparing(StockSnapshotView::categoryName, Comparator.nullsLast(String::compareToIgnoreCase));
            case "warehouseName" -> Comparator.comparing(StockSnapshotView::warehouseName, Comparator.nullsLast(String::compareToIgnoreCase));
            case "quantity" -> Comparator.comparingLong(StockSnapshotView::quantity);
            default -> Comparator.comparing(StockSnapshotView::sku, Comparator.nullsLast(String::compareToIgnoreCase));
        };
        if (!ascending) {
            comparator = comparator.reversed();
        }
        return input.stream().sorted(comparator).toList();
    }

    public static List<LowStockView> sortLowStockViews(List<LowStockView> input, String property, boolean ascending) {
        Comparator<LowStockView> comparator = switch (property) {
            case "sku" -> Comparator.comparing(LowStockView::sku, Comparator.nullsLast(String::compareToIgnoreCase));
            case "productName" -> Comparator.comparing(LowStockView::productName, Comparator.nullsLast(String::compareToIgnoreCase));
            case "warehouseName" -> Comparator.comparing(LowStockView::warehouseName, Comparator.nullsLast(String::compareToIgnoreCase));
            case "deficit" -> Comparator.comparingLong(LowStockView::deficit);
            case "quantity" -> Comparator.comparingLong(LowStockView::quantity);
            default -> Comparator.comparingLong(LowStockView::deficit);
        };
        if (!ascending) {
            comparator = comparator.reversed();
        }
        return input.stream().sorted(comparator).toList();
    }

    public static List<ExpiringItemView> sortExpiringViews(List<ExpiringItemView> input, String property, boolean ascending) {
        Comparator<ExpiringItemView> comparator = switch (property) {
            case "sku" -> Comparator.comparing(ExpiringItemView::sku, Comparator.nullsLast(String::compareToIgnoreCase));
            case "productName" -> Comparator.comparing(ExpiringItemView::productName, Comparator.nullsLast(String::compareToIgnoreCase));
            case "warehouseName" -> Comparator.comparing(ExpiringItemView::warehouseName, Comparator.nullsLast(String::compareToIgnoreCase));
            case "expiryDate" -> Comparator.comparing(ExpiringItemView::expiryDate, Comparator.nullsLast(LocalDate::compareTo));
            case "daysUntilExpiry" -> Comparator.comparingLong(ExpiringItemView::daysUntilExpiry);
            default -> Comparator.comparingLong(ExpiringItemView::daysUntilExpiry);
        };
        if (!ascending) {
            comparator = comparator.reversed();
        }
        return input.stream().sorted(comparator).toList();
    }

    private static String likePattern(String value) {
        return "%" + value.trim().toLowerCase() + "%";
    }

    public static <T> List<T> paginate(List<T> data, int page, int size) {
        if (size <= 0) {
            return Collections.emptyList();
        }
        int fromIndex = Math.min(page * size, data.size());
        int toIndex = Math.min(fromIndex + size, data.size());
        if (fromIndex > toIndex) {
            return Collections.emptyList();
        }
        return data.subList(fromIndex, toIndex);
    }

    public static <T> long countDistinctVariant(List<T> data) {
        if (data.isEmpty()) {
            return 0L;
        }
        if (data.get(0) instanceof StockSnapshotView snapshot) {
            Set<UUID> ids = data.stream()
                    .map(StockSnapshotView.class::cast)
                    .map(StockSnapshotView::variantId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            return ids.size();
        }
        return data.size();
    }
}
