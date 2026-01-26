package br.com.stockshift.service;

import br.com.stockshift.dto.sale.CancelSaleRequest;
import br.com.stockshift.dto.sale.CreateSaleRequest;
import br.com.stockshift.dto.sale.SaleItemRequest;
import br.com.stockshift.dto.sale.SaleResponse;
import br.com.stockshift.exception.InsufficientStockException;
import br.com.stockshift.exception.InvalidSaleCancellationException;
import br.com.stockshift.exception.SaleNotFoundException;
import br.com.stockshift.model.entity.*;
import br.com.stockshift.model.enums.PaymentMethod;
import br.com.stockshift.model.enums.SaleStatus;
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
        warehouseId = UUID.fromString("00000001-0000-0000-0000-000000000000");
        productId = UUID.fromString("00000001-0000-0000-0000-000000000000");
        userId = UUID.fromString("00000005-0000-0000-0000-000000000000");
        
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
        user.setFullName("Test User");
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
    
    @Test
    void shouldCreateSaleSuccessfully() {
        // Given
        CreateSaleRequest request = new CreateSaleRequest();
        request.setWarehouseId(1L);
        request.setPaymentMethod(PaymentMethod.CASH);
        request.setDiscount(BigDecimal.ZERO);
        
        SaleItemRequest itemRequest = new SaleItemRequest();
        itemRequest.setProductId(1L);
        itemRequest.setQuantity(10);
        itemRequest.setUnitPrice(BigDecimal.TEN);
        request.setItems(List.of(itemRequest));
        
        Batch batch = new Batch();
        batch.setId(UUID.fromString("00000002-0000-0000-0000-000000000000"));
        batch.setQuantity(50);
        batch.setProduct(product);
        
        Sale savedSale = new Sale();
        savedSale.setId(UUID.fromString("00000003-0000-0000-0000-000000000000"));
        savedSale.setWarehouse(warehouse);
        savedSale.setUser(user);
        savedSale.setPaymentMethod(PaymentMethod.CASH);
        savedSale.setStatus(SaleStatus.COMPLETED);
        savedSale.setSubtotal(new BigDecimal("100.00"));
        savedSale.setDiscount(BigDecimal.ZERO);
        savedSale.setTotal(new BigDecimal("100.00"));
        savedSale.setCompletedAt(java.time.LocalDateTime.now());
        savedSale.setCreatedAt(java.time.LocalDateTime.now());
        
        SaleItem saleItem = new SaleItem();
        saleItem.setId(UUID.fromString("00000004-0000-0000-0000-000000000000"));
        saleItem.setProduct(product);
        saleItem.setQuantity(10);
        saleItem.setUnitPrice(BigDecimal.TEN);
        saleItem.setSubtotal(new BigDecimal("100.00"));
        saleItem.setBatch(batch);
        savedSale.addItem(saleItem);
        
        // Convert Long IDs to UUIDs (as the service does)
        UUID expectedWarehouseId = UUID.fromString(String.format("%08d-0000-0000-0000-000000000000", 1L));
        UUID expectedProductId = UUID.fromString(String.format("%08d-0000-0000-0000-000000000000", 1L));
        
        when(warehouseRepository.findById(expectedWarehouseId)).thenReturn(Optional.of(warehouse));
        when(productRepository.findById(expectedProductId)).thenReturn(Optional.of(product));
        when(batchService.getAvailableQuantity(expectedProductId, expectedWarehouseId, tenantId)).thenReturn(50);
        when(batchRepository.findByProductIdAndWarehouseIdAndTenantId(expectedProductId, warehouseId, tenantId))
            .thenReturn(List.of(batch));
        when(saleRepository.save(any(Sale.class))).thenReturn(savedSale);
        
        // When
        SaleResponse response = saleService.createSale(request, user);
        
        // Then
        assertNotNull(response);
        assertNotNull(response.getId());
        verify(saleRepository, times(1)).save(any(Sale.class));
        verify(batchRepository, times(1)).save(batch);
        assertEquals(40, batch.getQuantity()); // 50 - 10
    }
    
    @Test
    void shouldGetSaleById() {
        // Given
        Sale sale = new Sale();
        sale.setId(UUID.fromString("00000001-0000-0000-0000-000000000000"));
        sale.setWarehouse(warehouse);
        sale.setUser(user);
        sale.setTenantId(tenantId);
        sale.setPaymentMethod(PaymentMethod.CASH);
        sale.setStatus(SaleStatus.COMPLETED);
        sale.setSubtotal(BigDecimal.TEN);
        sale.setTotal(BigDecimal.TEN);
        
        UUID saleUuid = UUID.fromString("00000001-0000-0000-0000-000000000000");
        when(saleRepository.findByIdAndTenantId(saleUuid, tenantId)).thenReturn(Optional.of(sale));
        
        // When
        SaleResponse response = saleService.getSaleById(1L, tenantId);
        
        // Then
        assertNotNull(response);
        assertEquals(1L, response.getId());
    }
    
    @Test
    void shouldThrowExceptionWhenSaleNotFound() {
        // Given
        UUID saleUuid = UUID.fromString("00000001-0000-0000-0000-000000000000");
        when(saleRepository.findByIdAndTenantId(saleUuid, tenantId)).thenReturn(Optional.empty());
        
        // When & Then
        assertThrows(SaleNotFoundException.class, 
            () -> saleService.getSaleById(1L, tenantId));
    }
    
    @Test
    void shouldCancelSaleSuccessfully() {
        // Given
        Sale sale = new Sale();
        sale.setId(UUID.fromString("00000001-0000-0000-0000-000000000000"));
        sale.setWarehouse(warehouse);
        sale.setUser(user);
        sale.setTenantId(tenantId);
        sale.setPaymentMethod(PaymentMethod.CASH);
        sale.setStatus(SaleStatus.COMPLETED);
        sale.setSubtotal(new BigDecimal("100.00"));
        sale.setTotal(new BigDecimal("100.00"));
        
        SaleItem item = new SaleItem();
        item.setId(UUID.fromString("00000003-0000-0000-0000-000000000000"));
        item.setProduct(product);
        item.setQuantity(10);
        item.setUnitPrice(BigDecimal.TEN);
        item.setSubtotal(new BigDecimal("100.00"));
        
        Batch batch = new Batch();
        batch.setId(UUID.fromString("00000002-0000-0000-0000-000000000000"));
        batch.setQuantity(40);
        item.setBatch(batch);
        
        sale.addItem(item);
        
        CancelSaleRequest request = new CancelSaleRequest();
        request.setReason("Customer changed mind");
        
        UUID saleUuid = UUID.fromString("00000001-0000-0000-0000-000000000000");
        when(saleRepository.findByIdAndTenantId(saleUuid, tenantId)).thenReturn(Optional.of(sale));
        when(batchRepository.findById(batch.getId())).thenReturn(Optional.of(batch));
        when(saleRepository.save(any(Sale.class))).thenReturn(sale);
        
        // When
        SaleResponse response = saleService.cancelSale(1L, request, user);
        
        // Then
        assertNotNull(response);
        assertEquals(SaleStatus.CANCELLED, sale.getStatus());
        assertEquals(50, batch.getQuantity()); // 40 + 10
        verify(batchRepository, times(1)).save(batch);
    }
    
    @Test
    void shouldThrowExceptionWhenCancellingAlreadyCancelledSale() {
        // Given
        Sale sale = new Sale();
        sale.setId(UUID.fromString("00000001-0000-0000-0000-000000000000"));
        sale.setStatus(SaleStatus.CANCELLED);
        sale.setTenantId(tenantId);
        
        UUID saleUuid = UUID.fromString("00000001-0000-0000-0000-000000000000");
        when(saleRepository.findByIdAndTenantId(saleUuid, tenantId)).thenReturn(Optional.of(sale));
        
        CancelSaleRequest request = new CancelSaleRequest();
        request.setReason("Test");
        
        // When & Then
        assertThrows(InvalidSaleCancellationException.class,
            () -> saleService.cancelSale(1L, request, user));
    }
}
