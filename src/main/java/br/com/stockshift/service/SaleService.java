package br.com.stockshift.service;

import br.com.stockshift.dto.sale.*;
import br.com.stockshift.exception.*;
import br.com.stockshift.model.entity.*;
import br.com.stockshift.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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
        
        throw new UnsupportedOperationException("Not implemented yet");
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
}

