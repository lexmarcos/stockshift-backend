package br.com.stockshift.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.stockshift.BaseIntegrationTest;
import br.com.stockshift.dto.warehouse.WarehouseRequest;
import br.com.stockshift.model.entity.Brand;
import br.com.stockshift.model.entity.Category;
import br.com.stockshift.model.entity.Product;
import br.com.stockshift.model.entity.Role;
import br.com.stockshift.model.entity.Tenant;
import br.com.stockshift.model.entity.User;
import br.com.stockshift.model.entity.UserRoleWarehouse;
import br.com.stockshift.model.entity.UserRoleWarehouseId;
import br.com.stockshift.model.entity.Warehouse;
import br.com.stockshift.repository.BatchRepository;
import br.com.stockshift.repository.BrandRepository;
import br.com.stockshift.repository.CategoryRepository;
import br.com.stockshift.repository.ProductRepository;
import br.com.stockshift.repository.RoleRepository;
import br.com.stockshift.repository.TenantRepository;
import br.com.stockshift.repository.UserRoleWarehouseRepository;
import br.com.stockshift.repository.UserRepository;
import br.com.stockshift.repository.WarehouseRepository;
import br.com.stockshift.security.TenantContext;
import br.com.stockshift.security.UserPrincipal;
import br.com.stockshift.security.WarehouseContext;
import br.com.stockshift.util.TestDataFactory;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

class WarehouseControllerIntegrationTest extends BaseIntegrationTest {

        private ObjectMapper objectMapper = new ObjectMapper();

        @Autowired
        private WarehouseRepository warehouseRepository;

        @Autowired
        private TenantRepository tenantRepository;

        @Autowired
        private UserRepository userRepository;

        @Autowired
        private RoleRepository roleRepository;

        @Autowired
        private UserRoleWarehouseRepository userRoleWarehouseRepository;

        @Autowired
        private PasswordEncoder passwordEncoder;

        @Autowired
        private ProductRepository productRepository;

        @Autowired
        private CategoryRepository categoryRepository;

        @Autowired
        private BrandRepository brandRepository;

        @Autowired
        private BatchRepository batchRepository;

        private Tenant testTenant;

        @BeforeEach
        void setUpTestData() {
                WarehouseContext.clear();
                userRoleWarehouseRepository.deleteAll();
                batchRepository.deleteAll();
                productRepository.deleteAll();
                categoryRepository.deleteAll();
                brandRepository.deleteAll();
                warehouseRepository.deleteAll();
                userRepository.deleteAll();
                roleRepository.deleteAll();
                tenantRepository.deleteAll();

                testTenant = TestDataFactory.createTenant(tenantRepository, "Warehouse Test Tenant", "33333333000103");
                TestDataFactory.createUser(userRepository, passwordEncoder,
                                testTenant.getId(), "warehouse@test.com");

                TenantContext.setTenantId(testTenant.getId());
        }

        @Test
        @WithMockUser(username = "warehouse@test.com", authorities = { "ROLE_ADMIN" })
        void shouldCreateWarehouse() throws Exception {
                WarehouseRequest request = WarehouseRequest.builder()
                                .name("Main Warehouse")
                                .address("123 Storage St")
                                .city("São Paulo")
                                .state("SP")
                                .isActive(true)
                                .build();

                mockMvc.perform(post("/api/warehouses")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.success").value(true))
                                .andExpect(jsonPath("$.data.name").value("Main Warehouse"))
                                .andExpect(jsonPath("$.data.address").value("123 Storage St"));
        }

        @Test
        @WithMockUser(username = "warehouse@test.com", authorities = { "ROLE_ADMIN" })
        void shouldGetWarehouseById() throws Exception {
                Warehouse warehouse = TestDataFactory.createWarehouse(warehouseRepository,
                                testTenant.getId(), "Storage Unit A");

                mockMvc.perform(get("/api/warehouses/{id}", warehouse.getId()))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.success").value(true))
                                .andExpect(jsonPath("$.data.id").value(warehouse.getId().toString()))
                                .andExpect(jsonPath("$.data.name").value("Storage Unit A"));
        }

        @Test
        @WithMockUser(username = "warehouse@test.com", authorities = { "ROLE_ADMIN" })
        void shouldListAllWarehouses() throws Exception {
                TestDataFactory.createWarehouse(warehouseRepository, testTenant.getId(), "Warehouse 1");
                TestDataFactory.createWarehouse(warehouseRepository, testTenant.getId(), "Warehouse 2");

                mockMvc.perform(get("/api/warehouses"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.success").value(true))
                                .andExpect(jsonPath("$.data").isArray())
                                .andExpect(jsonPath("$.data.length()").value(2));
        }

        @Test
        @WithMockUser(username = "warehouse@test.com", authorities = { "products:read" })
        void shouldAllowAuthenticatedUserToLoadWarehouseSelection() throws Exception {
                mockMvc.perform(get("/api/warehouses"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.success").value(true))
                                .andExpect(jsonPath("$.data").isArray());
        }

        @Test
        @WithMockUser(username = "warehouse@test.com", authorities = { "products:read" })
        void shouldAllowAuthenticatedUserToLoadWarehouseStockSummaries() throws Exception {
                mockMvc.perform(get("/api/warehouses/stock-summary"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.success").value(true))
                                .andExpect(jsonPath("$.data").isArray());
        }

        @Test
        @WithMockUser(username = "warehouse@test.com", authorities = { "ROLE_ADMIN" })
        void shouldReturnProductsWithStockForWarehouse() throws Exception {
                // Given: warehouse, category, brand, products, and batches
                Warehouse warehouse = TestDataFactory.createWarehouse(warehouseRepository,
                                testTenant.getId(), "Main Warehouse");

                Category category = TestDataFactory.createCategory(categoryRepository,
                                testTenant.getId(), "Electronics");

                Brand brand = TestDataFactory.createBrand(brandRepository,
                                testTenant.getId(), "TestBrand");

                Product product1 = TestDataFactory.createProduct(productRepository,
                                testTenant.getId(), "Product 1", "SKU001", category, brand);

                Product product2 = TestDataFactory.createProduct(productRepository,
                                testTenant.getId(), "Product 2", "SKU002", category, brand);

                // Product 1 has 2 batches: 10 + 15 = 25 total
                TestDataFactory.createBatch(batchRepository, testTenant.getId(),
                                product1, warehouse, "BATCH001", 10);
                TestDataFactory.createBatch(batchRepository, testTenant.getId(),
                                product1, warehouse, "BATCH002", 15);

                // Product 2 has 1 batch: 30 total
                TestDataFactory.createBatch(batchRepository, testTenant.getId(),
                                product2, warehouse, "BATCH003", 30);

                // When & Then
                mockMvc.perform(get("/api/warehouses/{id}/products", warehouse.getId())
                                .param("page", "0")
                                .param("size", "10"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.success").value(true))
                                .andExpect(jsonPath("$.data.content").isArray())
                                .andExpect(jsonPath("$.data.content.length()").value(2))
                                .andExpect(jsonPath("$.data.content[0].totalQuantity").exists())
                                .andExpect(jsonPath("$.data.totalElements").value(2));
        }

        @Test
        void shouldReturnProductsWithStockForCurrentWarehouseWithProductReadPermission() throws Exception {
                Warehouse warehouse = TestDataFactory.createWarehouse(warehouseRepository,
                                testTenant.getId(), "Products Warehouse");

                Category category = TestDataFactory.createCategory(categoryRepository,
                                testTenant.getId(), "Products Category");

                Brand brand = TestDataFactory.createBrand(brandRepository,
                                testTenant.getId(), "Products Brand");

                Product product = TestDataFactory.createProduct(productRepository,
                                testTenant.getId(), "Readable Product", "SKU-READ", category, brand);

                TestDataFactory.createBatch(batchRepository, testTenant.getId(),
                                product, warehouse, "BATCH-READ", 8);

                WarehouseContext.setWarehouseId(warehouse.getId());
                assignWarehouseToUser(warehouse);

                mockMvc.perform(get("/api/warehouses/{id}/products", warehouse.getId())
                                .with(authentication(productReadAuthentication(warehouse))))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.success").value(true))
                                .andExpect(jsonPath("$.data.content.length()").value(1))
                                .andExpect(jsonPath("$.data.content[0].name").value("Readable Product"));
        }

        private void assignWarehouseToUser(Warehouse warehouse) {
                User user = userRepository.findByEmail("warehouse@test.com").orElseThrow();
                Role role = new Role();
                role.setTenantId(testTenant.getId());
                role.setName("VENDEDOR");
                role.setDescription("Seller");
                role.setIsSystemRole(true);
                role = roleRepository.save(role);

                UserRoleWarehouse assignment = new UserRoleWarehouse();
                assignment.setId(new UserRoleWarehouseId(user.getId(), role.getId(), warehouse.getId()));
                assignment.setUser(user);
                assignment.setRole(role);
                assignment.setWarehouse(warehouse);
                userRoleWarehouseRepository.saveAndFlush(assignment);
        }

        private Authentication productReadAuthentication(Warehouse warehouse) {
                User user = userRepository.findByEmail("warehouse@test.com").orElseThrow();
                UserPrincipal principal = new UserPrincipal(
                                user.getId(),
                                user.getTenantId(),
                                user.getEmail(),
                                user.getPassword(),
                                user.getIsActive(),
                                java.util.List.of(new SimpleGrantedAuthority("products:read")),
                                java.util.Set.of(warehouse.getId()),
                                false);

                return new UsernamePasswordAuthenticationToken(
                                principal,
                                principal.getPassword(),
                                principal.getAuthorities());
        }

        @Test
        @WithMockUser(username = "warehouse@test.com", authorities = { "ROLE_ADMIN" })
        void shouldReturnProductWithZeroStock() throws Exception {
                // Given: warehouse and product with batch quantity = 0
                Warehouse warehouse = TestDataFactory.createWarehouse(warehouseRepository,
                                testTenant.getId(), "Storage A");

                Category category = TestDataFactory.createCategory(categoryRepository,
                                testTenant.getId(), "Category A");

                Brand brand = TestDataFactory.createBrand(brandRepository,
                                testTenant.getId(), "Brand A");

                Product product = TestDataFactory.createProduct(productRepository,
                                testTenant.getId(), "Zero Stock Product", "SKU-ZERO", category, brand);

                // Batch with zero quantity
                TestDataFactory.createBatch(batchRepository, testTenant.getId(),
                                product, warehouse, "BATCH-ZERO", 0);

                // When & Then
                mockMvc.perform(get("/api/warehouses/{id}/products", warehouse.getId()))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.success").value(true))
                                .andExpect(jsonPath("$.data.content.length()").value(1))
                                .andExpect(jsonPath("$.data.content[0].name").value("Zero Stock Product"))
                                .andExpect(jsonPath("$.data.content[0].totalQuantity").value(0));
        }

        @Test
        @WithMockUser(username = "warehouse@test.com", authorities = { "ROLE_ADMIN" })
        void shouldReturnEmptyPageWhenWarehouseHasNoProducts() throws Exception {
                // Given: warehouse with no batches
                Warehouse warehouse = TestDataFactory.createWarehouse(warehouseRepository,
                                testTenant.getId(), "Empty Warehouse");

                // When & Then
                mockMvc.perform(get("/api/warehouses/{id}/products", warehouse.getId()))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.success").value(true))
                                .andExpect(jsonPath("$.data.content").isArray())
                                .andExpect(jsonPath("$.data.content.length()").value(0))
                                .andExpect(jsonPath("$.data.totalElements").value(0));
        }

        @Test
        @WithMockUser(username = "warehouse@test.com", authorities = { "ROLE_ADMIN" })
        void shouldReturn404WhenWarehouseNotFound() throws Exception {
                // Given: non-existent warehouse ID
                UUID nonExistentId = UUID.randomUUID();

                // When & Then
                mockMvc.perform(get("/api/warehouses/{id}/products", nonExistentId))
                                .andExpect(status().isNotFound());
        }

        @Test
        @WithMockUser(username = "warehouse@test.com", authorities = { "ROLE_ADMIN" })
        void shouldRespectPagination() throws Exception {
                // Given: warehouse with 10 products
                Warehouse warehouse = TestDataFactory.createWarehouse(warehouseRepository,
                                testTenant.getId(), "Large Warehouse");

                Category category = TestDataFactory.createCategory(categoryRepository,
                                testTenant.getId(), "Test Category");

                Brand brand = TestDataFactory.createBrand(brandRepository,
                                testTenant.getId(), "Test Brand");

                for (int i = 1; i <= 10; i++) {
                        Product product = TestDataFactory.createProduct(productRepository,
                                        testTenant.getId(), "Product " + i, "SKU" + i, category, brand);
                        TestDataFactory.createBatch(batchRepository, testTenant.getId(),
                                        product, warehouse, "BATCH" + i, i * 10);
                }

                // When & Then: page 0, size 5
                mockMvc.perform(get("/api/warehouses/{id}/products", warehouse.getId())
                                .param("page", "0")
                                .param("size", "5"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.content.length()").value(5))
                                .andExpect(jsonPath("$.data.totalElements").value(10))
                                .andExpect(jsonPath("$.data.totalPages").value(2))
                                .andExpect(jsonPath("$.data.number").value(0));

                // When & Then: page 1, size 5
                TenantContext.setTenantId(testTenant.getId());
                mockMvc.perform(get("/api/warehouses/{id}/products", warehouse.getId())
                                .param("page", "1")
                                .param("size", "5"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.content.length()").value(5))
                                .andExpect(jsonPath("$.data.number").value(1));
        }

        @Test
        @WithMockUser(username = "warehouse@test.com", authorities = { "ROLE_ADMIN" })
        void shouldExcludeSoftDeletedProducts() throws Exception {
                // Given: warehouse with active and soft-deleted products
                Warehouse warehouse = TestDataFactory.createWarehouse(warehouseRepository,
                                testTenant.getId(), "Test Warehouse");

                Category category = TestDataFactory.createCategory(categoryRepository,
                                testTenant.getId(), "Category");

                Brand brand = TestDataFactory.createBrand(brandRepository,
                                testTenant.getId(), "Brand");

                Product activeProduct = TestDataFactory.createProduct(productRepository,
                                testTenant.getId(), "Active Product", "SKU-ACTIVE", category, brand);

                Product deletedProduct = TestDataFactory.createProduct(productRepository,
                                testTenant.getId(), "Deleted Product", "SKU-DELETED", category, brand);
                deletedProduct.setDeletedAt(java.time.LocalDateTime.now());
                productRepository.save(deletedProduct);

                TestDataFactory.createBatch(batchRepository, testTenant.getId(),
                                activeProduct, warehouse, "BATCH-ACTIVE", 100);
                TestDataFactory.createBatch(batchRepository, testTenant.getId(),
                                deletedProduct, warehouse, "BATCH-DELETED", 50);

                // When & Then
                mockMvc.perform(get("/api/warehouses/{id}/products", warehouse.getId()))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.content.length()").value(1))
                                .andExpect(jsonPath("$.data.content[0].name").value("Active Product"));
        }

        @Test
        @Disabled("Sorting by aggregated fields not supported in current implementation")
        @WithMockUser(username = "warehouse@test.com", authorities = { "ROLE_ADMIN" })
        void shouldSupportSorting() throws Exception {
                // Given: warehouse with products with different quantities
                Warehouse warehouse = TestDataFactory.createWarehouse(warehouseRepository,
                                testTenant.getId(), "Sortable Warehouse");

                Category category = TestDataFactory.createCategory(categoryRepository,
                                testTenant.getId(), "Sort Category");

                Brand brand = TestDataFactory.createBrand(brandRepository,
                                testTenant.getId(), "Sort Brand");

                Product productLow = TestDataFactory.createProduct(productRepository,
                                testTenant.getId(), "Low Stock", "SKU-LOW", category, brand);
                Product productMid = TestDataFactory.createProduct(productRepository,
                                testTenant.getId(), "Mid Stock", "SKU-MID", category, brand);
                Product productHigh = TestDataFactory.createProduct(productRepository,
                                testTenant.getId(), "High Stock", "SKU-HIGH", category, brand);

                TestDataFactory.createBatch(batchRepository, testTenant.getId(),
                                productLow, warehouse, "BATCH-LOW", 5);
                TestDataFactory.createBatch(batchRepository, testTenant.getId(),
                                productMid, warehouse, "BATCH-MID", 50);
                TestDataFactory.createBatch(batchRepository, testTenant.getId(),
                                productHigh, warehouse, "BATCH-HIGH", 200);

                // When & Then: sort by totalQuantity descending
                mockMvc.perform(get("/api/warehouses/{id}/products", warehouse.getId())
                                .param("sort", "totalQuantity,desc"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.content[0].name").value("High Stock"))
                                .andExpect(jsonPath("$.data.content[0].totalQuantity").value(200))
                                .andExpect(jsonPath("$.data.content[2].name").value("Low Stock"))
                                .andExpect(jsonPath("$.data.content[2].totalQuantity").value(5));
        }
}
