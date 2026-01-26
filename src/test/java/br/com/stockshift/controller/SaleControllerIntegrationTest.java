package br.com.stockshift.controller;

import br.com.stockshift.BaseIntegrationTest;
import br.com.stockshift.dto.sale.CancelSaleRequest;
import br.com.stockshift.dto.sale.CreateSaleRequest;
import br.com.stockshift.dto.sale.SaleItemRequest;
import br.com.stockshift.model.entity.*;
import br.com.stockshift.model.enums.PaymentMethod;
import br.com.stockshift.model.enums.SaleStatus;
import br.com.stockshift.repository.*;
import br.com.stockshift.util.TestDataFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SaleControllerIntegrationTest extends BaseIntegrationTest {

    private ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private SaleRepository saleRepository;
    
    @Autowired
    private ProductRepository productRepository;
    
    @Autowired
    private WarehouseRepository warehouseRepository;
    
    @Autowired
    private BatchRepository batchRepository;
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private TenantRepository tenantRepository;
    
    @Autowired
    private CategoryRepository categoryRepository;
    
    @Autowired
    private PasswordEncoder passwordEncoder;
    
    private Tenant testTenant;
    private Product product;
    private Warehouse warehouse;
    private Batch batch;
    private User testUser;
    private Category testCategory;

    @BeforeEach
    void setUp() {
        saleRepository.deleteAll();
        batchRepository.deleteAll();
        productRepository.deleteAll();
        categoryRepository.deleteAll();
        warehouseRepository.deleteAll();
        userRepository.deleteAll();
        tenantRepository.deleteAll();
        
        // Create test data
        testTenant = TestDataFactory.createTenant(tenantRepository, "Sale Test Tenant", "11111111000101");
        testUser = TestDataFactory.createUser(userRepository, passwordEncoder, testTenant.getId(), "testuser@test.com");
        testCategory = TestDataFactory.createCategory(categoryRepository, testTenant.getId(), "Test Category");
        warehouse = TestDataFactory.createWarehouse(warehouseRepository, testTenant.getId(), "Test Warehouse");
        product = TestDataFactory.createProduct(productRepository, testTenant.getId(), testCategory, "Test Product", "TEST-SKU");
        batch = TestDataFactory.createBatch(batchRepository, testTenant.getId(), product, warehouse, "BATCH-001", 100);
    }
    
    private Long convertUUIDToLong(java.util.UUID uuid) {
        String uuidStr = uuid.toString();
        String numericPart = uuidStr.substring(0, 8);
        return Long.parseLong(numericPart);
    }

    @Test
    @WithMockUser(username = "testuser@test.com")
    void shouldCreateSaleSuccessfully() throws Exception {
        // Given
        CreateSaleRequest request = new CreateSaleRequest();
        request.setWarehouseId(convertUUIDToLong(warehouse.getId()));
        request.setPaymentMethod(PaymentMethod.CASH);
        request.setDiscount(BigDecimal.ZERO);
        
        SaleItemRequest item = new SaleItemRequest();
        item.setProductId(convertUUIDToLong(product.getId()));
        item.setQuantity(10);
        item.setUnitPrice(new BigDecimal("50.00"));
        request.setItems(List.of(item));
        
        // When & Then
        mockMvc.perform(post("/api/sales")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.paymentMethod").value("CASH"))
                .andExpect(jsonPath("$.subtotal").value(500.00))
                .andExpect(jsonPath("$.total").value(500.00))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].quantity").value(10));
        
        // Verify stock was reduced
        Batch updatedBatch = batchRepository.findById(batch.getId()).orElseThrow();
        assertThat(updatedBatch.getQuantity()).isEqualTo(90);
    }

    @Test
    @WithMockUser(username = "testuser@test.com")
    void shouldFailWhenInsufficientStock() throws Exception {
        // Given
        CreateSaleRequest request = new CreateSaleRequest();
        request.setWarehouseId(convertUUIDToLong(warehouse.getId()));
        request.setPaymentMethod(PaymentMethod.CASH);
        
        SaleItemRequest item = new SaleItemRequest();
        item.setProductId(convertUUIDToLong(product.getId()));
        item.setQuantity(200); // More than available
        item.setUnitPrice(new BigDecimal("50.00"));
        request.setItems(List.of(item));
        
        // When & Then
        mockMvc.perform(post("/api/sales")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "testuser@test.com")
    void shouldGetSaleById() throws Exception {
        // Given - Create a sale first
        Sale sale = new Sale();
        sale.setWarehouse(warehouse);
        sale.setUser(testUser);
        sale.setTenantId(testTenant.getId());
        sale.setPaymentMethod(PaymentMethod.CASH);
        sale.setStatus(SaleStatus.COMPLETED);
        sale.setSubtotal(new BigDecimal("100.00"));
        sale.setDiscount(BigDecimal.ZERO);
        sale.setTotal(new BigDecimal("100.00"));
        sale = saleRepository.save(sale);
        
        // When & Then
        mockMvc.perform(get("/api/sales/" + convertUUIDToLong(sale.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(convertUUIDToLong(sale.getId())))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    @WithMockUser(username = "testuser@test.com")
    void shouldCancelSaleAndReturnStock() throws Exception {
        // Given - Create a sale
        Sale sale = new Sale();
        sale.setWarehouse(warehouse);
        sale.setUser(testUser);
        sale.setTenantId(testTenant.getId());
        sale.setPaymentMethod(PaymentMethod.CASH);
        sale.setStatus(SaleStatus.COMPLETED);
        sale.setSubtotal(new BigDecimal("100.00"));
        sale.setDiscount(BigDecimal.ZERO);
        sale.setTotal(new BigDecimal("100.00"));
        
        SaleItem item = new SaleItem();
        item.setProduct(product);
        item.setBatch(batch);
        item.setQuantity(10);
        item.setUnitPrice(new BigDecimal("10.00"));
        item.setSubtotal(new BigDecimal("100.00"));
        sale.addItem(item);
        
        sale = saleRepository.save(sale);
        
        // Reduce stock manually
        batch.setQuantity(90);
        batchRepository.save(batch);
        
        CancelSaleRequest request = new CancelSaleRequest();
        request.setReason("Customer changed mind");
        
        // When & Then
        mockMvc.perform(put("/api/sales/" + convertUUIDToLong(sale.getId()) + "/cancel")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancellationReason").value("Customer changed mind"));
        
        // Verify stock was returned
        Batch updatedBatch = batchRepository.findById(batch.getId()).orElseThrow();
        assertThat(updatedBatch.getQuantity()).isEqualTo(100);
    }

    @Test
    @WithMockUser(username = "testuser@test.com")
    void shouldListAllSales() throws Exception {
        // Given - Create some sales
        for (int i = 0; i < 3; i++) {
            Sale sale = new Sale();
            sale.setWarehouse(warehouse);
            sale.setUser(testUser);
            sale.setTenantId(testTenant.getId());
            sale.setPaymentMethod(PaymentMethod.CASH);
            sale.setStatus(SaleStatus.COMPLETED);
            sale.setSubtotal(new BigDecimal("100.00"));
            sale.setDiscount(BigDecimal.ZERO);
            sale.setTotal(new BigDecimal("100.00"));
            saleRepository.save(sale);
        }
        
        // When & Then
        mockMvc.perform(get("/api/sales"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(3)))
                .andExpect(jsonPath("$.totalElements").value(3));
    }
}
