package br.com.stockshift.util;

import br.com.stockshift.model.entity.*;
import br.com.stockshift.model.enums.BarcodeType;
import br.com.stockshift.repository.*;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public class TestDataFactory {

    public static Tenant createTenant(TenantRepository repo, String name, String document) {
        Tenant tenant = new Tenant();
        tenant.setBusinessName(name);
        tenant.setDocument(document);
        tenant.setEmail(document + "@test.com");
        tenant.setIsActive(true);
        return repo.save(tenant);
    }

    public static User createUser(UserRepository repo, PasswordEncoder encoder,
                                   UUID tenantId, String email) {
        User user = new User();
        user.setTenantId(tenantId);
        user.setEmail(email);
        user.setPassword(encoder.encode("password123"));
        user.setFullName("Test User");
        user.setIsActive(true);
        return repo.save(user);
    }

    public static Category createCategory(CategoryRepository repo, UUID tenantId, String name) {
        Category category = new Category();
        category.setTenantId(tenantId);
        category.setName(name);
        return repo.save(category);
    }

    public static Product createProduct(ProductRepository repo, UUID tenantId,
                                        Category category, String name, String sku) {
        Product product = new Product();
        product.setTenantId(tenantId);
        product.setName(name);
        product.setCategory(category);
        product.setBarcode(sku + "-BARCODE");
        product.setBarcodeType(BarcodeType.EXTERNAL);
        product.setSku(sku);
        product.setIsKit(false);
        product.setHasExpiration(false);
        product.setActive(true);
        return repo.save(product);
    }

    public static Warehouse createWarehouse(WarehouseRepository repo, UUID tenantId, String name) {
        Warehouse warehouse = new Warehouse();
        warehouse.setTenantId(tenantId);
        warehouse.setName(name);
        warehouse.setAddress("Test Address 123");
        warehouse.setCity("Test City");
        warehouse.setState("SP");
        warehouse.setIsActive(true);
        return repo.save(warehouse);
    }

    public static Batch createBatch(BatchRepository repo, UUID tenantId,
                                    Product product, Warehouse warehouse, Integer quantity) {
        Batch batch = new Batch();
        batch.setTenantId(tenantId);
        batch.setProduct(product);
        batch.setWarehouse(warehouse);
        batch.setBatchCode("BATCH-" + UUID.randomUUID().toString().substring(0, 8));
        batch.setQuantity(quantity);
        batch.setCostPrice(BigDecimal.valueOf(10.00));
        batch.setSellingPrice(BigDecimal.valueOf(15.00));
        batch.setExpirationDate(LocalDate.now().plusMonths(6));
        return repo.save(batch);
    }
}
