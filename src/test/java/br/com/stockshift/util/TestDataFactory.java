package br.com.stockshift.util;

import br.com.stockshift.model.entity.*;
import br.com.stockshift.model.enums.BarcodeType;
import br.com.stockshift.model.enums.MovementDirection;
import br.com.stockshift.model.enums.StockMovementType;
import br.com.stockshift.model.enums.TransferStatus;
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

    public static Brand createBrand(BrandRepository repo, UUID tenantId, String name) {
        Brand brand = new Brand();
        brand.setTenantId(tenantId);
        brand.setName(name);
        return repo.save(brand);
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

    public static Product createProduct(ProductRepository repo, UUID tenantId,
                                        String name, String sku, Category category, Brand brand) {
        Product product = new Product();
        product.setTenantId(tenantId);
        product.setName(name);
        product.setSku(sku);
        product.setCategory(category);
        product.setBrand(brand);
        product.setBarcode(sku + "-BARCODE");
        product.setBarcodeType(BarcodeType.EXTERNAL);
        product.setActive(true);
        product.setIsKit(false);
        product.setHasExpiration(false);
        return repo.save(product);
    }

    public static Warehouse createWarehouse(WarehouseRepository repo, UUID tenantId, String name) {
        Warehouse warehouse = new Warehouse();
        warehouse.setTenantId(tenantId);
        warehouse.setName(name);
        warehouse.setCode("WH-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
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
        batch.setQuantity(BigDecimal.valueOf(quantity));
        batch.setCostPrice(1000L);  // R$10.00 in cents
        batch.setSellingPrice(1500L);  // R$15.00 in cents
        batch.setExpirationDate(LocalDate.now().plusMonths(6));
        return repo.save(batch);
    }

    public static Batch createBatch(BatchRepository repo, UUID tenantId,
                                    Product product, Warehouse warehouse,
                                    String batchCode, Integer quantity) {
        Batch batch = new Batch();
        batch.setTenantId(tenantId);
        batch.setProduct(product);
        batch.setWarehouse(warehouse);
        batch.setBatchCode(batchCode);
        batch.setQuantity(BigDecimal.valueOf(quantity));
        batch.setCostPrice(1000L);  // R$10.00 in cents
        batch.setSellingPrice(1500L);  // R$15.00 in cents
        batch.setExpirationDate(LocalDate.now().plusMonths(6));
        return repo.save(batch);
    }

    public static StockMovement createStockMovement(StockMovementRepository repo, UUID tenantId,
                                                     UUID warehouseId, StockMovementType type,
                                                     MovementDirection direction, UUID createdByUserId) {
        StockMovement sm = new StockMovement();
        sm.setTenantId(tenantId);
        sm.setCode("SM-" + UUID.randomUUID().toString().substring(0, 8));
        sm.setWarehouseId(warehouseId);
        sm.setType(type);
        sm.setDirection(direction);
        sm.setCreatedByUserId(createdByUserId);
        return repo.save(sm);
    }

    public static StockMovementItem createStockMovementItem(StockMovementItemRepository repo,
                                                             StockMovement movement, Product product,
                                                             Batch batch, BigDecimal quantity) {
        StockMovementItem item = new StockMovementItem();
        item.setStockMovement(movement);
        item.setProductId(product.getId());
        item.setProductName(product.getName());
        item.setProductSku(product.getSku());
        item.setBatchId(batch.getId());
        item.setBatchCode(batch.getBatchCode());
        item.setQuantity(quantity);
        return repo.save(item);
    }

    public static Transfer createTransfer(TransferRepository repo, UUID tenantId,
                                           UUID sourceWarehouseId, UUID destinationWarehouseId,
                                           TransferStatus status, UUID createdByUserId) {
        Transfer transfer = new Transfer();
        transfer.setTenantId(tenantId);
        transfer.setCode("TR-" + UUID.randomUUID().toString().substring(0, 8));
        transfer.setSourceWarehouseId(sourceWarehouseId);
        transfer.setDestinationWarehouseId(destinationWarehouseId);
        transfer.setStatus(status);
        transfer.setCreatedByUserId(createdByUserId);
        return repo.save(transfer);
    }
}
