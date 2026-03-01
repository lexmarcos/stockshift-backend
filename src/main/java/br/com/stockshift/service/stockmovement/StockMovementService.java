package br.com.stockshift.service.stockmovement;

import br.com.stockshift.dto.stockmovement.*;
import br.com.stockshift.exception.BadRequestException;
import br.com.stockshift.exception.InsufficientStockException;
import br.com.stockshift.exception.ResourceNotFoundException;
import br.com.stockshift.mapper.StockMovementMapper;
import br.com.stockshift.model.entity.*;
import br.com.stockshift.model.enums.LedgerEntryType;
import br.com.stockshift.model.enums.MovementDirection;
import br.com.stockshift.model.enums.StockMovementType;
import br.com.stockshift.repository.*;
import br.com.stockshift.security.SecurityUtils;
import br.com.stockshift.security.TenantContext;
import br.com.stockshift.service.WarehouseAccessService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class StockMovementService {

  private static final int CODE_SEQUENCE_PADDING = 4;
  private static final Set<StockMovementType> TRANSFER_TYPES = Set.of(
      StockMovementType.TRANSFER_IN, StockMovementType.TRANSFER_OUT);

  private final StockMovementRepository movementRepository;
  private final StockMovementItemRepository movementItemRepository;
  private final BatchRepository batchRepository;
  private final ProductRepository productRepository;
  private final WarehouseRepository warehouseRepository;
  private final InventoryLedgerRepository ledgerRepository;
  private final StockMovementMapper mapper;
  private final SecurityUtils securityUtils;
  private final WarehouseAccessService warehouseAccessService;

  // ── Manual movement (usage, gift, loss, etc.) ──────────────────────────

  @Transactional
  public StockMovementResponse create(CreateStockMovementRequest request) {
    UUID tenantId = TenantContext.getTenantId();
    UUID warehouseId = securityUtils.getCurrentWarehouseId();
    UUID userId = securityUtils.getCurrentUserId();

    // Prevent manual creation of transfer types
    if (TRANSFER_TYPES.contains(request.getType())) {
      throw new BadRequestException("Transfer movements are created automatically by the Transfer module");
    }

    String code = generateCode(tenantId);
    MovementDirection direction = request.getType().getDirection();

    StockMovement movement = StockMovement.builder()
        .code(code)
        .warehouseId(warehouseId)
        .type(request.getType())
        .direction(direction)
        .notes(request.getNotes())
        .createdByUserId(userId)
        .build();
    movement.setTenantId(tenantId);

    // Process each item
    for (CreateStockMovementItemRequest itemReq : request.getItems()) {
      Product product = productRepository.findByTenantIdAndId(tenantId, itemReq.getProductId())
          .orElseThrow(() -> new ResourceNotFoundException("Product", "id", itemReq.getProductId()));

      if (direction == MovementDirection.OUT) {
        processOutItems(movement, product, itemReq.getQuantity(), warehouseId, tenantId, userId, request.getType());
      } else {
        processInItem(movement, product, itemReq.getQuantity(), warehouseId, tenantId, userId, request.getType());
      }
    }

    StockMovement saved = movementRepository.save(movement);
    log.info("StockMovement {} ({}) created by user {} in warehouse {}",
        saved.getCode(), saved.getType(), userId, warehouseId);

    String warehouseName = warehouseRepository.findById(warehouseId)
        .map(Warehouse::getName).orElse("Unknown");
    return mapper.toResponse(saved, warehouseName);
  }

  private void processOutItems(StockMovement movement, Product product, BigDecimal quantity,
      UUID warehouseId, UUID tenantId, UUID userId, StockMovementType type) {
    List<Batch> batches = batchRepository.findByProductAndWarehouseForFifo(
        product.getId(), warehouseId, tenantId);

    BigDecimal remaining = quantity;
    BigDecimal totalAvailable = batches.stream()
        .map(Batch::getQuantity)
        .reduce(BigDecimal.ZERO, BigDecimal::add);

    if (totalAvailable.compareTo(quantity) < 0) {
      throw new InsufficientStockException(
          "Insufficient stock for product '" + product.getName() +
              "'. Available: " + totalAvailable + ", Required: " + quantity);
    }

    LedgerEntryType ledgerType = mapToLedgerType(type);

    for (Batch batch : batches) {
      if (remaining.compareTo(BigDecimal.ZERO) <= 0)
        break;

      BigDecimal deductAmount = remaining.min(batch.getQuantity());
      batch.setQuantity(batch.getQuantity().subtract(deductAmount));
      batchRepository.save(batch);

      StockMovementItem item = StockMovementItem.builder()
          .productId(product.getId())
          .productName(product.getName())
          .productSku(product.getSku())
          .batchId(batch.getId())
          .batchCode(batch.getBatchCode())
          .quantity(deductAmount)
          .build();
      movement.addItem(item);

      // Create ledger entry
      InventoryLedger ledger = InventoryLedger.builder()
          .tenantId(tenantId)
          .warehouseId(warehouseId)
          .batchId(batch.getId())
          .productId(product.getId())
          .entryType(ledgerType)
          .quantity(deductAmount.negate())
          .balanceAfter(batch.getQuantity())
          .referenceType("STOCK_MOVEMENT")
          .referenceId(movement.getId())
          .notes(type.name() + ": " + (movement.getNotes() != null ? movement.getNotes() : ""))
          .createdBy(userId)
          .build();
      ledgerRepository.save(ledger);

      remaining = remaining.subtract(deductAmount);
    }
  }

  private void processInItem(StockMovement movement, Product product, BigDecimal quantity,
      UUID warehouseId, UUID tenantId, UUID userId, StockMovementType type) {
    Warehouse warehouse = warehouseRepository.findByTenantIdAndId(tenantId, warehouseId)
        .orElseThrow(() -> new ResourceNotFoundException("Warehouse", "id", warehouseId));

    // For IN movements, create a new batch or add to first existing batch
    List<Batch> existingBatches = batchRepository.findByProductIdAndWarehouseIdAndTenantId(
        product.getId(), warehouseId, tenantId);

    Batch batch;
    if (!existingBatches.isEmpty()) {
      batch = existingBatches.get(0);
      batch.setQuantity(batch.getQuantity().add(quantity));
      batchRepository.save(batch);
    } else {
      String batchCode = "MOV-" + LocalDate.now() + "-" + product.getSku();
      batch = Batch.builder()
          .product(product)
          .warehouse(warehouse)
          .batchCode(batchCode)
          .quantity(quantity)
          .transitQuantity(BigDecimal.ZERO)
          .build();
      batch.setTenantId(tenantId);
      batch = batchRepository.save(batch);
    }

    StockMovementItem item = StockMovementItem.builder()
        .productId(product.getId())
        .productName(product.getName())
        .productSku(product.getSku())
        .batchId(batch.getId())
        .batchCode(batch.getBatchCode())
        .quantity(quantity)
        .build();
    movement.addItem(item);

    LedgerEntryType ledgerType = mapToLedgerType(type);
    InventoryLedger ledger = InventoryLedger.builder()
        .tenantId(tenantId)
        .warehouseId(warehouseId)
        .batchId(batch.getId())
        .productId(product.getId())
        .entryType(ledgerType)
        .quantity(quantity)
        .balanceAfter(batch.getQuantity())
        .referenceType("STOCK_MOVEMENT")
        .referenceId(movement.getId())
        .notes(type.name() + ": " + (movement.getNotes() != null ? movement.getNotes() : ""))
        .createdBy(userId)
        .build();
    ledgerRepository.save(ledger);
  }

  // ── Transfer integration (called by TransferService) ───────────────────

  @Transactional
  public StockMovement createForTransfer(UUID tenantId, UUID warehouseId, UUID userId,
      StockMovementType type, UUID transferId,
      List<StockMovementItem> items, String notes) {
    String code = generateCode(tenantId);

    StockMovement movement = StockMovement.builder()
        .code(code)
        .warehouseId(warehouseId)
        .type(type)
        .direction(type.getDirection())
        .notes(notes)
        .createdByUserId(userId)
        .referenceType("TRANSFER")
        .referenceId(transferId)
        .build();
    movement.setTenantId(tenantId);

    for (StockMovementItem item : items) {
      movement.addItem(item);
    }

    StockMovement saved = movementRepository.save(movement);
    log.info("StockMovement {} ({}) created for transfer {} in warehouse {}",
        saved.getCode(), saved.getType(), transferId, warehouseId);
    return saved;
  }

  // ── Read operations ────────────────────────────────────────────────────

  @Transactional(readOnly = true)
  public StockMovementResponse getById(UUID id) {
    UUID tenantId = TenantContext.getTenantId();

    StockMovement movement = movementRepository.findByTenantIdAndId(tenantId, id)
        .orElseThrow(() -> new ResourceNotFoundException("StockMovement", "id", id));

    String warehouseName = warehouseRepository.findById(movement.getWarehouseId())
        .map(Warehouse::getName).orElse("Unknown");
    return mapper.toResponse(movement, warehouseName);
  }

  @Transactional(readOnly = true)
  public Page<StockMovementResponse> list(UUID warehouseId, UUID productId,
      StockMovementType type,
      LocalDateTime dateFrom, LocalDateTime dateTo,
      Pageable pageable) {
    UUID tenantId = TenantContext.getTenantId();
    UUID currentWarehouseId = securityUtils.getCurrentWarehouseId();

    // Use current warehouse if not specified
    UUID effectiveWarehouseId = warehouseId != null ? warehouseId : currentWarehouseId;

    Page<StockMovement> movements;
    if (productId != null) {
      movements = movementRepository.findExtract(tenantId, effectiveWarehouseId, productId, type, dateFrom, dateTo,
          pageable);
    } else {
      movements = movementRepository.findWithFilters(tenantId, effectiveWarehouseId, type, dateFrom, dateTo, pageable);
    }

    return movements.map(m -> {
      String name = warehouseRepository.findById(m.getWarehouseId())
          .map(Warehouse::getName).orElse("Unknown");
      return mapper.toResponse(m, name);
    });
  }

  @Transactional(readOnly = true)
  public WarehouseMovementSummaryResponse getWarehouseSummary(LocalDateTime dateFrom, LocalDateTime dateTo) {
    UUID tenantId = TenantContext.getTenantId();

    // Get all warehouses for this tenant
    List<Warehouse> warehouses = warehouseRepository.findAllByTenantId(tenantId);
    List<UUID> warehouseIds = warehouses.stream().map(Warehouse::getId).collect(Collectors.toList());

    List<StockMovement> movements = movementRepository.findForWarehouseSummary(
        tenantId, warehouseIds, dateFrom, dateTo);

    // Group by warehouse
    Map<UUID, List<StockMovement>> byWarehouse = movements.stream()
        .collect(Collectors.groupingBy(StockMovement::getWarehouseId));

    Map<UUID, String> warehouseNames = warehouses.stream()
        .collect(Collectors.toMap(Warehouse::getId, Warehouse::getName));

    List<WarehouseMovementSummaryResponse.WarehouseSummary> summaries = new ArrayList<>();

    for (Warehouse wh : warehouses) {
      List<StockMovement> whMovements = byWarehouse.getOrDefault(wh.getId(), Collections.emptyList());

      Map<StockMovementType, List<StockMovement>> byType = whMovements.stream()
          .collect(Collectors.groupingBy(StockMovement::getType));

      BigDecimal totalIn = BigDecimal.ZERO;
      BigDecimal totalOut = BigDecimal.ZERO;
      List<WarehouseMovementSummaryResponse.TypeSummary> typeSummaries = new ArrayList<>();

      for (Map.Entry<StockMovementType, List<StockMovement>> entry : byType.entrySet()) {
        BigDecimal qty = entry.getValue().stream()
            .flatMap(m -> m.getItems().stream())
            .map(StockMovementItem::getQuantity)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (entry.getKey().getDirection() == MovementDirection.IN) {
          totalIn = totalIn.add(qty);
        } else {
          totalOut = totalOut.add(qty);
        }

        typeSummaries.add(WarehouseMovementSummaryResponse.TypeSummary.builder()
            .type(entry.getKey().name())
            .direction(entry.getKey().getDirection().name())
            .totalQuantity(qty)
            .count(entry.getValue().size())
            .build());
      }

      summaries.add(WarehouseMovementSummaryResponse.WarehouseSummary.builder()
          .warehouseId(wh.getId())
          .warehouseName(wh.getName())
          .movementsByType(typeSummaries)
          .totalIn(totalIn)
          .totalOut(totalOut)
          .build());
    }

    return WarehouseMovementSummaryResponse.builder()
        .warehouses(summaries)
        .build();
  }

  // ── Helpers ─────────────────────────────────────────────────────────────

  private String generateCode(UUID tenantId) {
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

  private LedgerEntryType mapToLedgerType(StockMovementType type) {
    return switch (type) {
      case USAGE -> LedgerEntryType.USAGE_OUT;
      case GIFT -> LedgerEntryType.GIFT_OUT;
      case LOSS -> LedgerEntryType.LOSS_OUT;
      case DAMAGE -> LedgerEntryType.DAMAGE_OUT;
      case PURCHASE_IN -> LedgerEntryType.STOCK_MOVEMENT_IN;
      case ADJUSTMENT_IN -> LedgerEntryType.ADJUSTMENT_IN;
      case ADJUSTMENT_OUT -> LedgerEntryType.ADJUSTMENT_OUT;
      case TRANSFER_IN -> LedgerEntryType.TRANSFER_IN;
      case TRANSFER_OUT -> LedgerEntryType.TRANSFER_OUT;
      default -> throw new IllegalArgumentException("Unknown movement type: " + type);
    };
  }
}
