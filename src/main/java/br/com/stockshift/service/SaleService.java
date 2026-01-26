package br.com.stockshift.service;

import br.com.stockshift.dto.sale.*;
import br.com.stockshift.exception.*;
import br.com.stockshift.model.entity.*;
import br.com.stockshift.model.enums.SaleStatus;
import br.com.stockshift.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SaleService {

    private final SaleRepository saleRepository;
    private final ProductRepository productRepository;
    private final WarehouseRepository warehouseRepository;
    private final BatchRepository batchRepository;
    private final BatchService batchService;
    private final StockMovementService stockMovementService;

    @Transactional
    public SaleResponse createSale(CreateSaleRequest request, User user) {
        log.info("Creating sale for user {} at warehouse {}", user.getId(), request.getWarehouseId());
        
        // Convert Long IDs from DTO to UUID for entity lookups
        UUID warehouseUuid = convertLongToUUID(request.getWarehouseId());
        
        // Validate warehouse
        Warehouse warehouse = warehouseRepository.findById(warehouseUuid)
            .orElseThrow(() -> new ResourceNotFoundException("Warehouse not found"));
        
        // Validate items and stock
        validateSaleItems(request, warehouse.getTenantId());
        
        // Create sale entity
        Sale sale = new Sale();
        sale.setTenantId(user.getTenantId());
        sale.setWarehouse(warehouse);
        sale.setUser(user);
        sale.setCustomerId(request.getCustomerId());
        sale.setCustomerName(request.getCustomerName());
        sale.setPaymentMethod(request.getPaymentMethod());
        sale.setStatus(SaleStatus.COMPLETED);
        sale.setDiscount(request.getDiscount() != null ? request.getDiscount() : BigDecimal.ZERO);
        sale.setNotes(request.getNotes());
        sale.setCompletedAt(java.time.LocalDateTime.now());
        
        // Process items and reduce stock
        for (SaleItemRequest itemRequest : request.getItems()) {
            Product product = productRepository.findById(convertLongToUUID(itemRequest.getProductId())).orElseThrow();
            
            SaleItem saleItem = new SaleItem();
            saleItem.setProduct(product);
            saleItem.setQuantity(itemRequest.getQuantity());
            saleItem.setUnitPrice(itemRequest.getUnitPrice());
            saleItem.calculateSubtotal();
            
            // Reduce stock using FIFO
            reduceStockFromBatches(itemRequest, warehouse, user.getTenantId(), saleItem);
            
            sale.addItem(saleItem);
        }
        
        // Calculate totals
        sale.calculateTotals();
        
        // Save sale
        sale = saleRepository.save(sale);
        
        log.info("Sale created successfully: {}", sale.getId());
        
        return mapToResponse(sale);
    }
    
    @Transactional(readOnly = true)
    public SaleResponse getSaleById(Long id, UUID tenantId) {
        UUID saleUuid = convertLongToUUID(id);
        Sale sale = saleRepository.findByIdAndTenantId(saleUuid, tenantId)
            .orElseThrow(() -> new SaleNotFoundException("Sale not found: " + id));
        
        return mapToResponse(sale);
    }

    @Transactional(readOnly = true)
    public Page<SaleResponse> getAllSales(UUID tenantId, Pageable pageable) {
        return saleRepository.findAllByTenantId(tenantId, pageable)
            .map(this::mapToResponse);
    }
    
    @Transactional
    public SaleResponse cancelSale(Long id, CancelSaleRequest request, User user) {
        log.info("Cancelling sale {} by user {}", id, user.getId());
        
        UUID saleUuid = convertLongToUUID(id);
        Sale sale = saleRepository.findByIdAndTenantId(saleUuid, user.getTenantId())
            .orElseThrow(() -> new SaleNotFoundException("Sale not found: " + id));
        
        // Validate cancellation
        if (sale.getStatus() == SaleStatus.CANCELLED) {
            throw new InvalidSaleCancellationException("Sale is already cancelled");
        }
        
        // Return stock to batches
        for (SaleItem item : sale.getItems()) {
            if (item.getBatch() != null) {
                Batch batch = batchRepository.findById(item.getBatch().getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Batch not found"));
                
                batch.setQuantity(batch.getQuantity() + item.getQuantity());
                batchRepository.save(batch);
            }
        }
        
        // Update sale status
        sale.setStatus(SaleStatus.CANCELLED);
        sale.setCancelledAt(java.time.LocalDateTime.now());
        sale.setCancelledBy(user);
        sale.setCancellationReason(request.getReason());
        
        sale = saleRepository.save(sale);
        
        log.info("Sale {} cancelled successfully", id);
        
        return mapToResponse(sale);
    }
    
    private void validateSaleItems(CreateSaleRequest request, UUID tenantId) {
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new EmptySaleException("Sale must have at least one item");
        }
        
        UUID warehouseUuid = convertLongToUUID(request.getWarehouseId());
        
        for (SaleItemRequest item : request.getItems()) {
            UUID productUuid = convertLongToUUID(item.getProductId());
            
            // Validate product exists
            Product product = productRepository.findById(productUuid)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + item.getProductId()));
            
            if (!product.getActive()) {
                throw new BusinessException("Product is inactive: " + product.getName());
            }
            
            // Validate price
            if (item.getUnitPrice().compareTo(BigDecimal.ZERO) <= 0) {
                throw new InvalidPriceException("Unit price must be positive");
            }
            
            // Validate stock availability
            int availableStock = batchService.getAvailableQuantity(
                productUuid, 
                warehouseUuid, 
                tenantId
            );
            
            if (availableStock < item.getQuantity()) {
                throw new InsufficientStockException(
                    String.format("Insufficient stock for product %s. Available: %d, Required: %d",
                        product.getName(), availableStock, item.getQuantity())
                );
            }
        }
    }
    
    private UUID convertLongToUUID(Long id) {
        // Convert Long ID to UUID by formatting as string with leading zeros
        return UUID.fromString(String.format("%08d-0000-0000-0000-000000000000", id));
    }
    
    private void reduceStockFromBatches(SaleItemRequest itemRequest, Warehouse warehouse, 
                                        UUID tenantId, SaleItem saleItem) {
        List<Batch> availableBatches = batchRepository.findByProductIdAndWarehouseIdAndTenantId(
            convertLongToUUID(itemRequest.getProductId()), warehouse.getId(), tenantId);
        
        int remainingQuantity = itemRequest.getQuantity();
        
        for (Batch batch : availableBatches) {
            if (remainingQuantity <= 0) break;
            
            int quantityToReduce = Math.min(remainingQuantity, batch.getQuantity());
            batch.setQuantity(batch.getQuantity() - quantityToReduce);
            batchRepository.save(batch);
            
            // Set batch reference on first item (for tracking)
            if (saleItem.getBatch() == null) {
                saleItem.setBatch(batch);
            }
            
            remainingQuantity -= quantityToReduce;
        }
    }
    
    private SaleResponse mapToResponse(Sale sale) {
        return SaleResponse.builder()
            .id(convertUUIDToLong(sale.getId()))
            .warehouseId(convertUUIDToLong(sale.getWarehouse().getId()))
            .warehouseName(sale.getWarehouse().getName())
            .userId(convertUUIDToLong(sale.getUser().getId()))
            .userName(sale.getUser().getFullName())
            .customerId(sale.getCustomerId())
            .customerName(sale.getCustomerName())
            .paymentMethod(sale.getPaymentMethod())
            .status(sale.getStatus())
            .subtotal(sale.getSubtotal())
            .discount(sale.getDiscount())
            .total(sale.getTotal())
            .notes(sale.getNotes())
            .stockMovementId(sale.getStockMovement() != null ? convertUUIDToLong(sale.getStockMovement().getId()) : null)
            .createdAt(sale.getCreatedAt())
            .completedAt(sale.getCompletedAt())
            .cancelledAt(sale.getCancelledAt())
            .cancelledBy(sale.getCancelledBy() != null ? convertUUIDToLong(sale.getCancelledBy().getId()) : null)
            .cancelledByName(sale.getCancelledBy() != null ? sale.getCancelledBy().getFullName() : null)
            .cancellationReason(sale.getCancellationReason())
            .items(sale.getItems().stream().map(this::mapItemToResponse).toList())
            .build();
    }
    
    private SaleItemResponse mapItemToResponse(SaleItem item) {
        return SaleItemResponse.builder()
            .id(convertUUIDToLong(item.getId()))
            .productId(convertUUIDToLong(item.getProduct().getId()))
            .productName(item.getProduct().getName())
            .productSku(item.getProduct().getSku())
            .batchId(item.getBatch() != null ? convertUUIDToLong(item.getBatch().getId()) : null)
            .batchCode(item.getBatch() != null ? item.getBatch().getBatchCode() : null)
            .quantity(item.getQuantity())
            .unitPrice(item.getUnitPrice())
            .subtotal(item.getSubtotal())
            .build();
    }
    
    private Long convertUUIDToLong(UUID uuid) {
        // Extract the numeric part from formatted UUID
        String uuidStr = uuid.toString();
        String numericPart = uuidStr.substring(0, 8);
        return Long.parseLong(numericPart);
    }
}

