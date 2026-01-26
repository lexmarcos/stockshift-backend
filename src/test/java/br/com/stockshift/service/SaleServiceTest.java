package br.com.stockshift.service;

import br.com.stockshift.dto.sale.CreateSaleRequest;
import br.com.stockshift.dto.sale.SaleItemRequest;
import br.com.stockshift.exception.InsufficientStockException;
import br.com.stockshift.model.entity.*;
import br.com.stockshift.model.enums.PaymentMethod;
import br.com.stockshift.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SaleServiceTest {

    @Mock
    private SaleRepository saleRepository;
    
    @Mock
    private ProductRepository productRepository;
    
    @Mock
    private WarehouseRepository warehouseRepository;
    
    @Mock
    private BatchRepository batchRepository;
    
    @Mock
    private BatchService batchService;
    
    @Mock
    private StockMovementService stockMovementService;
    
    @InjectMocks
    private SaleService saleService;
    
    private UUID tenantId;
    private UUID warehouseId;
    private UUID productId;
    private UUID userId;
    private Tenant tenant;
    private Warehouse warehouse;
    private Product product;
    private User user;
    
    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        warehouseId = UUID.randomUUID();
        productId = UUID.randomUUID();
        userId = UUID.randomUUID();
        
        tenant = new Tenant();
        tenant.setId(tenantId);
        
        warehouse = new Warehouse();
        warehouse.setId(warehouseId);
        warehouse.setName("Main Warehouse");
        warehouse.setTenantId(tenantId);
        
        product = new Product();
        product.setId(productId);
        product.setName("Test Product");
        product.setActive(true);
        
        user = new User();
        user.setId(userId);
        user.setTenantId(tenantId);
    }
    
    @Test
    void shouldThrowExceptionWhenInsufficientStock() {
        // Given
        CreateSaleRequest request = new CreateSaleRequest();
        request.setWarehouseId(1L);
        request.setPaymentMethod(PaymentMethod.CASH);
        
        SaleItemRequest itemRequest = new SaleItemRequest();
        itemRequest.setProductId(1L);
        itemRequest.setQuantity(100);
        itemRequest.setUnitPrice(BigDecimal.TEN);
        request.setItems(List.of(itemRequest));
        
        // Convert Long IDs to UUIDs (as the service does)
        UUID expectedWarehouseId = UUID.fromString(String.format("%08d-0000-0000-0000-000000000000", 1L));
        UUID expectedProductId = UUID.fromString(String.format("%08d-0000-0000-0000-000000000000", 1L));
        
        when(warehouseRepository.findById(expectedWarehouseId)).thenReturn(Optional.of(warehouse));
        when(productRepository.findById(expectedProductId)).thenReturn(Optional.of(product));
        when(batchService.getAvailableQuantity(expectedProductId, expectedWarehouseId, tenantId)).thenReturn(50);
        
        // When & Then
        assertThrows(InsufficientStockException.class, 
            () -> saleService.createSale(request, user));
    }
}
