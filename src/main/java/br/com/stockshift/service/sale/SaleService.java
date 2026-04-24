package br.com.stockshift.service.sale;

import br.com.stockshift.dto.sale.*;
import br.com.stockshift.exception.BadRequestException;
import br.com.stockshift.exception.InsufficientStockException;
import br.com.stockshift.exception.ResourceNotFoundException;
import br.com.stockshift.mapper.SaleMapper;
import br.com.stockshift.model.entity.*;
import br.com.stockshift.model.enums.*;
import br.com.stockshift.model.entity.User;
import br.com.stockshift.repository.*;
import br.com.stockshift.repository.UserRepository;
import br.com.stockshift.security.SecurityUtils;
import br.com.stockshift.security.TenantContext;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SaleService {

    private static final int CODE_SEQUENCE_PADDING = 4;

    private final SaleRepository saleRepository;
    private final BatchRepository batchRepository;
    private final ProductRepository productRepository;
    private final WarehouseRepository warehouseRepository;
    private final InventoryLedgerRepository ledgerRepository;
    private final StockMovementRepository movementRepository;
    private final UserRepository userRepository;
    private final SaleMapper mapper;
    private final SecurityUtils securityUtils;
    private final InfinitePayCheckoutService infinitePayCheckoutService;
    private final TenantRepository tenantRepository;

    // ── Create sale ─────────────────────────────────────────────────────────

    @Transactional
    public SaleResponse create(CreateSaleRequest request) {
        UUID tenantId = TenantContext.getTenantId();
        UUID userId = securityUtils.getCurrentUserId();
        UUID warehouseId = request.getWarehouseId();

        // Validate warehouse belongs to tenant
        Warehouse warehouse = warehouseRepository.findByTenantIdAndId(tenantId, warehouseId)
                .orElseThrow(() -> new ResourceNotFoundException("Warehouse", "id", warehouseId));

        String code = generateCode(tenantId);

        // Calculate discountPercentage (default 0)
        BigDecimal discountPercentage = request.getDiscountPercentage() != null
                ? request.getDiscountPercentage()
                : BigDecimal.ZERO;

        // Build Sale entity
        Sale sale = Sale.builder()
                .code(code)
                .warehouseId(warehouseId)
                .paymentMethod(request.getPaymentMethod())
                .installments(request.getInstallments())
                .discountPercentage(discountPercentage)
                .subtotal(0L)
                .discountAmount(0L)
                .total(0L)
                .status(determineSaleStatus(request))
                .paymentMode(resolvePaymentMode(request))
                .createdByUserId(userId)
                .build();
        sale.setTenantId(tenantId);

        // Save sale immediately to get ID
        Sale savedSale = saleRepository.save(sale);

        // Generate stock movement code
        String movementCode = generateMovementCode(tenantId);

        StockMovement stockMovement = StockMovement.builder()
                .code(movementCode)
                .warehouseId(warehouseId)
                .type(StockMovementType.SALE)
                .direction(MovementDirection.OUT)
                .referenceType("SALE")
                .referenceId(savedSale.getId())
                .createdByUserId(userId)
                .build();
        stockMovement.setTenantId(tenantId);

        long subtotal = 0L;

        // Process each sale item
        for (CreateSaleItemRequest itemReq : request.getItems()) {
            Product product = productRepository.findByTenantIdAndId(tenantId, itemReq.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product", "id", itemReq.getProductId()));

            if (itemReq.getBatchId() != null) {
                // Manual batch selection
                Batch batch = batchRepository.findByIdForUpdate(itemReq.getBatchId())
                        .orElseThrow(() -> new ResourceNotFoundException("Batch", "id", itemReq.getBatchId()));

                if (!batch.getTenantId().equals(tenantId)) {
                    throw new BadRequestException("Batch does not belong to current tenant");
                }
                if (!batch.getWarehouse().getId().equals(warehouseId)) {
                    throw new BadRequestException("Batch does not belong to the specified warehouse");
                }
                if (batch.getQuantity().compareTo(itemReq.getQuantity()) < 0) {
                    throw new InsufficientStockException(
                            "Insufficient stock for product '" + product.getName()
                                    + "' in batch " + batch.getBatchCode()
                                    + ". Available: " + batch.getQuantity()
                                    + ", Required: " + itemReq.getQuantity());
                }

                subtotal += processBatchAllocation(savedSale, stockMovement, batch, product,
                        itemReq.getQuantity(), tenantId, warehouseId, userId, code);

            } else {
                // Auto-allocate FIFO
                List<Batch> availableBatches = getFifoBatches(
                        product.getId(), warehouseId, tenantId);

                BigDecimal totalAvailable = availableBatches.stream()
                        .map(Batch::getQuantity)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                if (totalAvailable.compareTo(itemReq.getQuantity()) < 0) {
                    throw new InsufficientStockException(
                            "Insufficient stock for product '" + product.getName()
                                    + "'. Available: " + totalAvailable
                                    + ", Required: " + itemReq.getQuantity());
                }

                BigDecimal remaining = itemReq.getQuantity();
                for (Batch fifoBatch : availableBatches) {
                    if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;

                    BigDecimal deductAmount = remaining.min(fifoBatch.getQuantity());
                    subtotal += processBatchAllocation(sale, stockMovement, fifoBatch, product,
                            deductAmount, tenantId, warehouseId, userId, code);

                    remaining = remaining.subtract(deductAmount);
                }
            }
        }

        // Calculate discount and total
        long discountAmount = discountPercentage.compareTo(BigDecimal.ZERO) > 0
                ? BigDecimal.valueOf(subtotal)
                    .multiply(discountPercentage)
                    .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP)
                    .longValue()
                : 0L;
        long total = subtotal - discountAmount;

        savedSale.setSubtotal(subtotal);
        savedSale.setDiscountAmount(discountAmount);
        savedSale.setTotal(total);

        Sale saved = saleRepository.save(savedSale);
        movementRepository.save(stockMovement);

        log.info("Sale {} created by user {} in warehouse {} - total: {}",
                saved.getCode(), userId, warehouseId, total);

        SaleResponse response = mapper.toResponse(saved, warehouse.getName());

        // Generate payment link for LINK mode
        if (resolvePaymentMode(request) == PaymentMode.LINK) {
            if (total < 100) {
                throw new BadRequestException("Total da venda deve ser no mínimo R$ 1,00 para gerar link de pagamento");
            }

            Tenant tenant = tenantRepository.findById(tenantId)
                    .orElseThrow(() -> new ResourceNotFoundException("Tenant", "id", tenantId));

            if (tenant.getInfinitepayHandle() == null || tenant.getInfinitepayHandle().isBlank()) {
                throw new BadRequestException("Configure o InfinitePay nas configurações da empresa antes de gerar link de pagamento");
            }

            // Aggregate items by product name
            Map<String, InfinitePayCheckoutService.CheckoutItem> aggregated = new HashMap<>();
            for (SaleItem item : saved.getItems()) {
                aggregated.merge(item.getProductName(),
                        new InfinitePayCheckoutService.CheckoutItem(
                                item.getQuantity().intValue(),
                                item.getTotalPrice(),
                                item.getProductName()),
                        (existing, newItem) -> {
                            existing.setQuantity(existing.getQuantity() + newItem.getQuantity());
                            existing.setPrice(existing.getPrice() + newItem.getPrice());
                            return existing;
                        });
            }

            InfinitePayCheckoutService.CheckoutLinkResponse linkResponse =
                    infinitePayCheckoutService.generatePaymentLink(
                            tenant.getInfinitepayHandle(),
                            aggregated.values().stream().toList(),
                            saved.getId().toString());

            saved.setPaymentLink(linkResponse.getUrl());
            saved.setInfinitepayInvoiceSlug(linkResponse.getSlug());
            saleRepository.save(saved);

            response.setPaymentLink(linkResponse.getUrl());
        }

        return response;
    }

    // ── Get sale by ID ──────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public SaleResponse getById(UUID id) {
        UUID tenantId = TenantContext.getTenantId();

        Sale sale = saleRepository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Sale", "id", id));

        String warehouseName = warehouseRepository.findById(sale.getWarehouseId())
                .map(Warehouse::getName).orElse("Unknown");
        return mapper.toResponse(sale, warehouseName);
    }

    // ── Payment mode helpers ──────────────────────────────────────────────────

    private SaleStatus determineSaleStatus(CreateSaleRequest request) {
        PaymentMode mode = resolvePaymentMode(request);
        if (mode == PaymentMode.LINK || mode == PaymentMode.TAP) {
            return SaleStatus.PENDING;
        }
        return SaleStatus.COMPLETED;
    }

    private PaymentMode resolvePaymentMode(CreateSaleRequest request) {
        if (request.getPaymentMode() != null) {
            return request.getPaymentMode();
        }
        if (Boolean.TRUE.equals(request.getUseInfinitePay())) {
            return PaymentMode.TAP;
        }
        return PaymentMode.DIRECT;
    }

    // ── List sales ──────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<SaleSummaryResponse> list(UUID warehouseId, PaymentMethod paymentMethod,
                                           SaleStatus status,
                                           LocalDateTime dateFrom, LocalDateTime dateTo,
                                           Pageable pageable) {
        UUID tenantId = TenantContext.getTenantId();

        Page<Sale> sales = saleRepository.findWithFilters(
                tenantId, warehouseId, paymentMethod, status, dateFrom, dateTo, pageable);

        return sales.map(sale -> {
            String warehouseName = warehouseRepository.findById(sale.getWarehouseId())
                    .map(Warehouse::getName).orElse("Unknown");
            String userName = userRepository.findById(sale.getCreatedByUserId())
                    .map(User::getFullName).orElse("Desconhecido");
            return mapper.toSummaryResponse(sale, warehouseName, userName);
        });
    }

    // ── Cancel sale ─────────────────────────────────────────────────────────

    @Transactional
    public SaleResponse cancel(UUID id, CancelSaleRequest request) {
        UUID tenantId = TenantContext.getTenantId();
        UUID userId = securityUtils.getCurrentUserId();

        Sale sale = saleRepository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Sale", "id", id));

        if (sale.getStatus() == SaleStatus.CANCELLED) {
            throw new BadRequestException("Sale is already cancelled");
        }

        sale.setStatus(SaleStatus.CANCELLED);
        sale.setCancelledByUserId(userId);
        sale.setCancelledAt(Instant.now());
        sale.setCancellationReason(request.getCancellationReason());

        // Generate stock movement for cancellation
        String movementCode = generateMovementCode(tenantId);

        StockMovement stockMovement = StockMovement.builder()
                .code(movementCode)
                .warehouseId(sale.getWarehouseId())
                .type(StockMovementType.PURCHASE_IN)
                .direction(MovementDirection.IN)
                .referenceType("SALE_CANCEL")
                .referenceId(sale.getId())
                .createdByUserId(userId)
                .build();
        stockMovement.setTenantId(tenantId);

        // Return stock for each item
        for (SaleItem item : sale.getItems()) {
            Batch batch = batchRepository.findByIdForUpdate(item.getBatchId())
                    .orElseThrow(() -> new ResourceNotFoundException("Batch", "id", item.getBatchId()));

            // Return stock to batch
            batch.setQuantity(batch.getQuantity().add(item.getQuantity()));
            batchRepository.save(batch);

            // Create StockMovementItem
            StockMovementItem movementItem = StockMovementItem.builder()
                    .productId(item.getProductId())
                    .productName(item.getProductName())
                    .productSku(item.getProductSku())
                    .batchId(item.getBatchId())
                    .batchCode(item.getBatchCode())
                    .quantity(item.getQuantity())
                    .build();
            stockMovement.addItem(movementItem);

            // Create InventoryLedger (SALE_CANCEL_IN, positive quantity)
            InventoryLedger ledger = InventoryLedger.builder()
                    .tenantId(tenantId)
                    .warehouseId(sale.getWarehouseId())
                    .batchId(item.getBatchId())
                    .productId(item.getProductId())
                    .entryType(LedgerEntryType.SALE_CANCEL_IN)
                    .quantity(item.getQuantity())
                    .balanceAfter(batch.getQuantity())
                    .referenceType("SALE_CANCEL")
                    .referenceId(sale.getId())
                    .notes("Sale cancelled: " + sale.getCode())
                    .createdBy(userId)
                    .build();
            ledgerRepository.save(ledger);
        }

        Sale saved = saleRepository.save(sale);
        movementRepository.save(stockMovement);

        log.info("Sale {} cancelled by user {}", saved.getCode(), userId);

        String warehouseName = warehouseRepository.findById(saved.getWarehouseId())
                .map(Warehouse::getName).orElse("Unknown");
        return mapper.toResponse(saved, warehouseName);
    }

    // ── Confirm InfinitePay payment ──────────────────────────────────────

    @Transactional
    public void confirmInfinitePayPayment(UUID saleId, String nsu, String aut, String cardBrand) {
        Sale sale = saleRepository.findById(saleId)
                .orElseThrow(() -> new ResourceNotFoundException("Sale", "id", saleId));

        if (sale.getStatus() != SaleStatus.PENDING) {
            log.warn("Sale {} is not PENDING (status: {}), ignoring InfinitePay callback", saleId, sale.getStatus());
            return;
        }

        sale.setStatus(SaleStatus.COMPLETED);
        sale.setInfinitepayNsu(nsu);
        sale.setInfinitepayAut(aut);
        sale.setInfinitepayCardBrand(cardBrand);

        saleRepository.save(sale);
        log.info("Sale {} confirmed via InfinitePay (nsu: {}, card_brand: {})", sale.getCode(), nsu, cardBrand);
    }

    // ── Confirm InfinitePay webhook (payment link) ──────────────────────────

    @Transactional
    public void confirmInfinitePayWebhook(UUID saleId, String transactionNsu,
                                           String captureMethod, String invoiceSlug,
                                           String receiptUrl, Integer installments) {
        Sale sale = saleRepository.findById(saleId)
                .orElseThrow(() -> new ResourceNotFoundException("Sale", "id", saleId));

        if (sale.getStatus() != SaleStatus.PENDING) {
            log.warn("Sale {} is not PENDING (status: {}), ignoring webhook", saleId, sale.getStatus());
            return;
        }

        sale.setStatus(SaleStatus.COMPLETED);
        sale.setInfinitepayNsu(transactionNsu);
        sale.setInfinitepayCardBrand(captureMethod);
        sale.setInfinitepayInvoiceSlug(invoiceSlug);

        PaymentMethod resolvedPaymentMethod = mapCaptureMethodToPaymentMethod(captureMethod);
        if (resolvedPaymentMethod != null) {
            sale.setPaymentMethod(resolvedPaymentMethod);
        }
        if (installments != null && installments > 0) {
            sale.setInstallments(installments);
        }

        saleRepository.save(sale);
        log.info("Sale {} confirmed via InfinitePay webhook (transaction_nsu: {}, capture_method: {}, installments: {})",
                sale.getCode(), transactionNsu, captureMethod, installments);
    }

    private PaymentMethod mapCaptureMethodToPaymentMethod(String captureMethod) {
        if (captureMethod == null) return null;
        return switch (captureMethod.toLowerCase()) {
            case "credit_card" -> PaymentMethod.CREDIT_CARD;
            case "debit_card" -> PaymentMethod.DEBIT_CARD;
            case "pix" -> PaymentMethod.PIX;
            default -> null;
        };
    }

    // ── Next code ───────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public NextSaleCodeResponse getNextCode() {
        UUID tenantId = TenantContext.getTenantId();
        String code = generateCode(tenantId);
        return NextSaleCodeResponse.builder().code(code).build();
    }

    // ── Private helpers ─────────────────────────────────────────────────────

    /**
     * Processes a single batch allocation: deducts stock, creates SaleItem,
     * StockMovementItem, and InventoryLedger. Returns the line total price.
     */
    private long processBatchAllocation(Sale sale, StockMovement stockMovement,
                                         Batch batch, Product product,
                                         BigDecimal deductQuantity,
                                         UUID tenantId, UUID warehouseId,
                                         UUID userId, String saleCode) {
        long unitPrice = batch.getSellingPrice() != null ? batch.getSellingPrice() : 0L;
        long itemTotalPrice = unitPrice * deductQuantity.longValue();

        // Deduct stock from batch
        batch.setQuantity(batch.getQuantity().subtract(deductQuantity));
        batchRepository.save(batch);

        // Create SaleItem
        SaleItem saleItem = SaleItem.builder()
                .productId(product.getId())
                .productName(product.getName())
                .productSku(product.getSku())
                .batchId(batch.getId())
                .batchCode(batch.getBatchCode())
                .quantity(deductQuantity)
                .unitPrice(unitPrice)
                .totalPrice(itemTotalPrice)
                .build();
        sale.addItem(saleItem);

        // Create StockMovementItem
        StockMovementItem movementItem = StockMovementItem.builder()
                .productId(product.getId())
                .productName(product.getName())
                .productSku(product.getSku())
                .batchId(batch.getId())
                .batchCode(batch.getBatchCode())
                .quantity(deductQuantity)
                .build();
        stockMovement.addItem(movementItem);

        // Create InventoryLedger (SALE_OUT, negative quantity)
        InventoryLedger ledger = InventoryLedger.builder()
                .tenantId(tenantId)
                .warehouseId(warehouseId)
                .batchId(batch.getId())
                .productId(product.getId())
                .entryType(LedgerEntryType.SALE_OUT)
                .quantity(deductQuantity.negate())
                .balanceAfter(batch.getQuantity())
                .referenceType("SALE")
                .referenceId(sale.getId())
                .notes("Sale: " + saleCode)
                .createdBy(userId)
                .build();
        ledgerRepository.save(ledger);

        return itemTotalPrice;
    }

    private String generateCode(UUID tenantId) {
        String prefix = "VND-" + LocalDate.now().getYear() + "-";
        String likePrefix = prefix + "%";
        String latestCode = saleRepository.findLatestCodeByTenantIdAndCodePrefix(tenantId, likePrefix);

        if (latestCode == null || latestCode.isBlank()) {
            return prefix + String.format("%0" + CODE_SEQUENCE_PADDING + "d", 1);
        }

        String sequencePart = latestCode.substring(prefix.length());
        try {
            long next = Long.parseLong(sequencePart) + 1;
            return prefix + String.format("%0" + CODE_SEQUENCE_PADDING + "d", next);
        } catch (NumberFormatException ex) {
            log.warn("Unexpected sale code format '{}' for tenant {}. Falling back to count-based sequence.",
                    latestCode, tenantId);
            long count = saleRepository.countByTenantIdAndCodePrefix(tenantId, likePrefix);
            return prefix + String.format("%0" + CODE_SEQUENCE_PADDING + "d", count + 1);
        }
    }

    private String generateMovementCode(UUID tenantId) {
        String prefix = "MOV-" + LocalDate.now().getYear() + "-";
        String latestCode = movementRepository.findLatestCodeByTenantIdAndCodePrefix(tenantId, prefix);

        if (latestCode == null || latestCode.isBlank()) {
            return prefix + String.format("%0" + CODE_SEQUENCE_PADDING + "d", 1);
        }

        String sequencePart = latestCode.substring(prefix.length());
        try {
            long next = Long.parseLong(sequencePart) + 1;
            return prefix + String.format("%0" + CODE_SEQUENCE_PADDING + "d", next);
        } catch (NumberFormatException ex) {
            log.warn("Unexpected movement code format '{}' for tenant {}. Falling back to count-based sequence.",
                    latestCode, tenantId);
            long count = movementRepository.countByTenantIdAndCodePrefix(tenantId, prefix);
            return prefix + String.format("%0" + CODE_SEQUENCE_PADDING + "d", count + 1);
        }
    }

    /**
     * Gets available batches for FIFO allocation, re-sorted by expiration date ASC (NULLS LAST),
     * then createdAt ASC as tiebreaker. The DB query already filters quantity > 0.
     */
    private List<Batch> getFifoBatches(UUID productId, UUID warehouseId, UUID tenantId) {
        List<Batch> batches = batchRepository.findByProductAndWarehouseForFifo(productId, warehouseId, tenantId);

        return batches.stream()
                .sorted(Comparator
                        .comparing(Batch::getExpirationDate, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Batch::getCreatedAt))
                .collect(Collectors.toList());
    }
}
