package com.stockshift.backend.api;

import com.stockshift.backend.api.dto.auth.LoginRequest;
import com.stockshift.backend.api.dto.auth.LoginResponse;
import com.stockshift.backend.api.dto.attribute.AttributeDefinitionResponse;
import com.stockshift.backend.api.dto.attribute.AttributeValueResponse;
import com.stockshift.backend.api.dto.attribute.CreateAttributeDefinitionRequest;
import com.stockshift.backend.api.dto.attribute.CreateAttributeValueRequest;
import com.stockshift.backend.api.dto.brand.BrandResponse;
import com.stockshift.backend.api.dto.brand.CreateBrandRequest;
import com.stockshift.backend.api.dto.category.CategoryResponse;
import com.stockshift.backend.api.dto.category.CreateCategoryRequest;
import com.stockshift.backend.api.dto.product.CreateProductRequest;
import com.stockshift.backend.api.dto.product.ProductResponse;
import com.stockshift.backend.api.dto.stock.CreateStockEventLineRequest;
import com.stockshift.backend.api.dto.stock.CreateStockEventRequest;
import com.stockshift.backend.api.dto.stock.StockEventResponse;
import com.stockshift.backend.api.dto.variant.CreateProductVariantRequest;
import com.stockshift.backend.api.dto.variant.ProductVariantResponse;
import com.stockshift.backend.api.dto.variant.CreateProductVariantRequest.VariantAttributePair;
import com.stockshift.backend.api.dto.warehouse.CreateWarehouseRequest;
import com.stockshift.backend.api.dto.warehouse.WarehouseResponse;
import com.stockshift.backend.domain.attribute.AttributeType;
import com.stockshift.backend.domain.stock.StockEventType;
import com.stockshift.backend.domain.stock.StockReasonCode;
import com.stockshift.backend.domain.warehouse.WarehouseType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:stockshift-reports-test-db;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "spring.jpa.properties.hibernate.globally_quoted_identifiers=true"
})
class ReportsApiE2ETest {

    private static final String TEST_USERNAME = "testuser";
    private static final String TEST_PASSWORD = "testpass123";

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @BeforeEach
    void configureRestTemplate() {
        restTemplate.getRestTemplate().setRequestFactory(new HttpComponentsClientHttpRequestFactory());
    }

    @Test
    @SuppressWarnings({"unchecked", "null"})
    void shouldReturnStockSnapshotForWarehouse() {
        String accessToken = authenticateTestUser();
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        UUID brandId = createTestBrand(accessToken, "Brand " + suffix);
        UUID categoryId = createTestCategory(accessToken, "Category " + suffix);
        UUID productId = createTestProduct(accessToken, "Product " + suffix, brandId, categoryId, null);
        UUID variantId = createTestVariant(accessToken, productId, "SKU-" + suffix);
        UUID warehouseId = createTestWarehouse(accessToken, "WH-" + suffix);

        createStockEvent(
                accessToken,
                warehouseId,
                variantId,
                StockEventType.INBOUND,
                StockReasonCode.PURCHASE,
                OffsetDateTime.now(ZoneOffset.UTC),
                120L
        );

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url("/api/v1/reports/stock-snapshot?warehouseId=" + warehouseId),
                HttpMethod.GET,
                new HttpEntity<>(authorizedHeaders(accessToken)),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();

        List<Map<String, Object>> content = (List<Map<String, Object>>) body.get("content");
        assertThat(content).isNotNull().hasSize(1);

        Map<String, Object> row = content.get(0);
        assertThat(UUID.fromString((String) row.get("variantId"))).isEqualTo(variantId);
        assertThat(UUID.fromString((String) row.get("warehouseId"))).isEqualTo(warehouseId);
        assertThat(((Number) row.get("quantity")).longValue()).isEqualTo(120L);
    }

    @Test
    @SuppressWarnings({"unchecked", "null"})
    void shouldReturnStockHistoryForVariant() {
        String accessToken = authenticateTestUser();
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        UUID brandId = createTestBrand(accessToken, "Brand " + suffix);
        UUID categoryId = createTestCategory(accessToken, "Category " + suffix);
        UUID productId = createTestProduct(accessToken, "Product " + suffix, brandId, categoryId, null);
        UUID variantId = createTestVariant(accessToken, productId, "SKU-" + suffix);
        UUID warehouseId = createTestWarehouse(accessToken, "WH-" + suffix);

        OffsetDateTime firstEventTime = OffsetDateTime.now(ZoneOffset.UTC).minusHours(2);
        OffsetDateTime secondEventTime = OffsetDateTime.now(ZoneOffset.UTC).minusHours(1);

        createStockEvent(
                accessToken,
                warehouseId,
                variantId,
                StockEventType.INBOUND,
                StockReasonCode.PURCHASE,
                firstEventTime,
                100L
        );

        createStockEvent(
                accessToken,
                warehouseId,
                variantId,
                StockEventType.OUTBOUND,
                StockReasonCode.SALE,
                secondEventTime,
                40L
        );

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url("/api/v1/reports/stock-history?variantId=" + variantId + "&warehouseId=" + warehouseId + "&sort=occurredAt,asc"),
                HttpMethod.GET,
                new HttpEntity<>(authorizedHeaders(accessToken)),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();

        List<Map<String, Object>> content = (List<Map<String, Object>>) body.get("content");
        assertThat(content).isNotNull().hasSize(2);

        Map<String, Object> first = content.get(0);
        Map<String, Object> second = content.get(1);

        assertThat(((Number) first.get("quantityChange")).longValue()).isEqualTo(100L);
        assertThat(((Number) first.get("balanceAfter")).longValue()).isEqualTo(100L);
        assertThat(((Number) second.get("quantityChange")).longValue()).isEqualTo(-40L);
        assertThat(((Number) second.get("balanceAfter")).longValue()).isEqualTo(60L);
    }

    @Test
    @SuppressWarnings({"unchecked", "null"})
    void shouldReturnLowStockItemsBelowThreshold() {
        String accessToken = authenticateTestUser();
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        UUID brandId = createTestBrand(accessToken, "Brand " + suffix);
        UUID categoryId = createTestCategory(accessToken, "Category " + suffix);
        UUID productId = createTestProduct(accessToken, "Product " + suffix, brandId, categoryId, null);
        UUID variantId = createTestVariant(accessToken, productId, "SKU-" + suffix);
        UUID warehouseId = createTestWarehouse(accessToken, "WH-" + suffix);

        createStockEvent(
                accessToken,
                warehouseId,
                variantId,
                StockEventType.INBOUND,
                StockReasonCode.PURCHASE,
                OffsetDateTime.now(ZoneOffset.UTC),
                10L
        );

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url("/api/v1/reports/low-stock?warehouseId=" + warehouseId + "&threshold=20"),
                HttpMethod.GET,
                new HttpEntity<>(authorizedHeaders(accessToken)),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();

        List<Map<String, Object>> content = (List<Map<String, Object>>) body.get("content");
        assertThat(content).isNotNull().hasSize(1);

        Map<String, Object> row = content.get(0);
        assertThat(UUID.fromString((String) row.get("variantId"))).isEqualTo(variantId);
        assertThat(((Number) row.get("quantity")).longValue()).isEqualTo(10L);
        assertThat(((Number) row.get("threshold")).longValue()).isEqualTo(20L);
        assertThat(((Number) row.get("deficit")).longValue()).isEqualTo(-10L);
    }

    @Test
    @SuppressWarnings({"unchecked", "null"})
    void shouldReturnExpiringItemsWithinWindow() {
        String accessToken = authenticateTestUser();
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        UUID brandId = createTestBrand(accessToken, "Brand " + suffix);
        UUID categoryId = createTestCategory(accessToken, "Category " + suffix);
        LocalDate expiryDate = LocalDate.now().plusDays(10);
        UUID productId = createTestProduct(accessToken, "Product " + suffix, brandId, categoryId, expiryDate);
        UUID variantId = createTestVariant(accessToken, productId, "SKU-" + suffix);
        UUID warehouseId = createTestWarehouse(accessToken, "WH-" + suffix);

        createStockEvent(
                accessToken,
                warehouseId,
                variantId,
                StockEventType.INBOUND,
                StockReasonCode.PURCHASE,
                OffsetDateTime.now(ZoneOffset.UTC),
                30L
        );

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url("/api/v1/reports/expiring?warehouseId=" + warehouseId + "&daysAhead=15"),
                HttpMethod.GET,
                new HttpEntity<>(authorizedHeaders(accessToken)),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();

        List<Map<String, Object>> content = (List<Map<String, Object>>) body.get("content");
        assertThat(content).isNotNull().hasSize(1);

        Map<String, Object> row = content.get(0);
        assertThat(UUID.fromString((String) row.get("variantId"))).isEqualTo(variantId);
        assertThat(((Number) row.get("quantity")).longValue()).isEqualTo(30L);
        assertThat(((Number) row.get("daysUntilExpiry")).longValue()).isEqualTo(10L);
    }

    private String authenticateTestUser() {
        LoginRequest loginRequest = new LoginRequest(TEST_USERNAME, TEST_PASSWORD);
        ResponseEntity<LoginResponse> response = restTemplate.postForEntity(
                url("/api/v1/auth/login"),
                loginRequest,
                LoginResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        LoginResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getAccessToken()).isNotBlank();
        return body.getAccessToken();
    }

    private UUID createTestBrand(String accessToken, String brandName) {
        CreateBrandRequest brandRequest = new CreateBrandRequest(brandName, "Test brand description");
        HttpEntity<CreateBrandRequest> entity = new HttpEntity<>(brandRequest, authorizedJsonHeaders(accessToken));

        ResponseEntity<BrandResponse> response = restTemplate.postForEntity(
                url("/api/v1/brands"),
                entity,
                BrandResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        BrandResponse brand = response.getBody();
        assertThat(brand).isNotNull();
        return brand.getId();
    }

    private UUID createTestCategory(String accessToken, String categoryName) {
        CreateCategoryRequest categoryRequest = new CreateCategoryRequest(categoryName, "Test category", null);
        HttpEntity<CreateCategoryRequest> entity = new HttpEntity<>(categoryRequest, authorizedJsonHeaders(accessToken));

        ResponseEntity<CategoryResponse> response = restTemplate.postForEntity(
                url("/api/v1/categories"),
                entity,
                CategoryResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        CategoryResponse category = response.getBody();
        assertThat(category).isNotNull();
        return category.getId();
    }

    private UUID createTestProduct(String accessToken, String productName, UUID brandId, UUID categoryId, LocalDate expiryDate) {
        LocalDate effectiveExpiry = expiryDate != null ? expiryDate : LocalDate.now().plusMonths(6);
        CreateProductRequest productRequest = new CreateProductRequest(
                productName,
                "Test product description",
                brandId,
                categoryId,
                10000L,
                effectiveExpiry
        );
        HttpEntity<CreateProductRequest> entity = new HttpEntity<>(productRequest, authorizedJsonHeaders(accessToken));

        ResponseEntity<ProductResponse> response = restTemplate.postForEntity(
                url("/api/v1/products"),
                entity,
                ProductResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        ProductResponse product = response.getBody();
        assertThat(product).isNotNull();
        return product.getId();
    }

    private UUID createTestVariant(String accessToken, UUID productId, String sku) {
        UUID definitionId = createTestAttributeDefinition(accessToken, "Color-" + sku, "COLOR-" + sku, AttributeType.ENUM);
        UUID valueId = createTestAttributeValue(accessToken, definitionId, "Red", "RED-" + sku);

        List<VariantAttributePair> attributes = new ArrayList<>();
        attributes.add(new VariantAttributePair(definitionId, valueId));

        CreateProductVariantRequest variantRequest = new CreateProductVariantRequest(
                sku,
                "GTIN-" + sku,
                attributes,
                12000L,
                500,
                10,
                5,
                15
        );

        HttpEntity<CreateProductVariantRequest> entity = new HttpEntity<>(variantRequest, authorizedJsonHeaders(accessToken));

        ResponseEntity<ProductVariantResponse> response = restTemplate.postForEntity(
                url("/api/v1/products/" + productId + "/variants"),
                entity,
                ProductVariantResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        ProductVariantResponse variant = response.getBody();
        assertThat(variant).isNotNull();
        return variant.getId();
    }

    private UUID createTestAttributeDefinition(String accessToken, String name, String code, AttributeType type) {
        CreateAttributeDefinitionRequest request = new CreateAttributeDefinitionRequest();
        request.setName(name);
        request.setCode(code);
        request.setType(type);
        request.setIsVariantDefining(true);

        HttpEntity<CreateAttributeDefinitionRequest> entity = new HttpEntity<>(request, authorizedJsonHeaders(accessToken));
        ResponseEntity<AttributeDefinitionResponse> response = restTemplate.postForEntity(
                url("/api/v1/attributes/definitions"),
                entity,
                AttributeDefinitionResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        AttributeDefinitionResponse definition = response.getBody();
        assertThat(definition).isNotNull();
        return definition.getId();
    }

    private UUID createTestAttributeValue(String accessToken, UUID definitionId, String value, String code) {
        CreateAttributeValueRequest request = new CreateAttributeValueRequest(value, code, null, null);
        HttpEntity<CreateAttributeValueRequest> entity = new HttpEntity<>(request, authorizedJsonHeaders(accessToken));

        ResponseEntity<AttributeValueResponse> response = restTemplate.postForEntity(
                url("/api/v1/attributes/definitions/" + definitionId + "/values"),
                entity,
                AttributeValueResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        AttributeValueResponse valueResponse = response.getBody();
        assertThat(valueResponse).isNotNull();
        return valueResponse.getId();
    }

    private UUID createTestWarehouse(String accessToken, String code) {
        CreateWarehouseRequest request = new CreateWarehouseRequest(
                code,
                "Warehouse " + code,
                "Test warehouse description",
                WarehouseType.STORE,
                "Test Address",
                "Test City",
                "TS",
                "12345",
                "Test Country",
                null,
                null,
                null
        );
        HttpEntity<CreateWarehouseRequest> entity = new HttpEntity<>(request, authorizedJsonHeaders(accessToken));

        ResponseEntity<WarehouseResponse> response = restTemplate.postForEntity(
                url("/api/v1/warehouses"),
                entity,
                WarehouseResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        WarehouseResponse warehouse = response.getBody();
        assertThat(warehouse).isNotNull();
        return warehouse.getId();
    }

    private UUID createStockEvent(
            String accessToken,
            UUID warehouseId,
            UUID variantId,
            StockEventType type,
            StockReasonCode reasonCode,
            OffsetDateTime occurredAt,
            Long quantity
    ) {
        List<CreateStockEventLineRequest> lines = new ArrayList<>();
        lines.add(new CreateStockEventLineRequest(variantId, quantity));

        CreateStockEventRequest request = new CreateStockEventRequest(
                type,
                warehouseId,
                occurredAt,
                reasonCode,
                null,
                lines
        );

        HttpEntity<CreateStockEventRequest> entity = new HttpEntity<>(request, authorizedJsonHeaders(accessToken));

        ResponseEntity<StockEventResponse> response = restTemplate.postForEntity(
                url("/api/v1/stock-events"),
                entity,
                StockEventResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        StockEventResponse event = response.getBody();
        assertThat(event).isNotNull();
        return event.getId();
    }

    private HttpHeaders authorizedJsonHeaders(String accessToken) {
        HttpHeaders headers = authorizedHeaders(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private HttpHeaders authorizedHeaders(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        return headers;
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }
}
