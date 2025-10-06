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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
                "spring.datasource.url=jdbc:h2:mem:stockshift-test-db;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.jpa.show-sql=false",
                "spring.jpa.properties.hibernate.globally_quoted_identifiers=true"
})
class StockEventApiE2ETest {

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
        @SuppressWarnings("null")
        void shouldCreateInboundStockEventSuccessfully() {
                String accessToken = authenticateTestUser();

                String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);

                // Create test data: brand, category, product, variant, warehouse
                UUID brandId = createTestBrand(accessToken, "Test Brand " + uniqueSuffix);
                UUID categoryId = createTestCategory(accessToken, "Test Category " + uniqueSuffix);
                UUID productId = createTestProduct(accessToken, "Test Product " + uniqueSuffix, brandId, categoryId);
                UUID variantId = createTestVariant(accessToken, productId, "SKU-" + uniqueSuffix);
                UUID warehouseId = createTestWarehouse(accessToken, "WH-" + uniqueSuffix);

                // Create INBOUND stock event
                List<CreateStockEventLineRequest> lines = new ArrayList<>();
                lines.add(new CreateStockEventLineRequest(variantId, 100L));

                CreateStockEventRequest createRequest = new CreateStockEventRequest(
                                StockEventType.INBOUND,
                                warehouseId,
                                OffsetDateTime.now(),
                                StockReasonCode.PURCHASE,
                                "Initial stock entry",
                                lines);

                HttpEntity<CreateStockEventRequest> createEntity = new HttpEntity<>(createRequest,
                                authorizedJsonHeaders(accessToken));
                ResponseEntity<StockEventResponse> createResponse = restTemplate.postForEntity(
                                url("/api/v1/stock-events"),
                                createEntity,
                                StockEventResponse.class);

                assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
                StockEventResponse createdEvent = createResponse.getBody();
                assertThat(createdEvent).isNotNull();
                assertThat(createdEvent.getId()).isNotNull();
                assertThat(createdEvent.getType()).isEqualTo(StockEventType.INBOUND);
                assertThat(createdEvent.getWarehouseId()).isEqualTo(warehouseId);
                assertThat(createdEvent.getReasonCode()).isEqualTo(StockReasonCode.PURCHASE);
                assertThat(createdEvent.getNotes()).isEqualTo("Initial stock entry");
                assertThat(createdEvent.getLines()).hasSize(1);
                assertThat(createdEvent.getLines().get(0).getVariantId()).isEqualTo(variantId);
                assertThat(createdEvent.getLines().get(0).getQuantity()).isEqualTo(100L);
        }

        @Test
        @SuppressWarnings("null")
        void shouldCreateOutboundStockEventSuccessfully() {
                String accessToken = authenticateTestUser();

                String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);

                UUID brandId = createTestBrand(accessToken, "Test Brand " + uniqueSuffix);
                UUID categoryId = createTestCategory(accessToken, "Test Category " + uniqueSuffix);
                UUID productId = createTestProduct(accessToken, "Test Product " + uniqueSuffix, brandId, categoryId);
                UUID variantId = createTestVariant(accessToken, productId, "SKU-" + uniqueSuffix);
                UUID warehouseId = createTestWarehouse(accessToken, "WH-" + uniqueSuffix);

                // Create INBOUND first to have stock
                createInboundEvent(accessToken, warehouseId, variantId, 200L);

                // Create OUTBOUND stock event
                List<CreateStockEventLineRequest> lines = new ArrayList<>();
                lines.add(new CreateStockEventLineRequest(variantId, 50L));

                CreateStockEventRequest createRequest = new CreateStockEventRequest(
                                StockEventType.OUTBOUND,
                                warehouseId,
                                OffsetDateTime.now(),
                                StockReasonCode.SALE,
                                "Sale to customer",
                                lines);

                HttpEntity<CreateStockEventRequest> createEntity = new HttpEntity<>(createRequest,
                                authorizedJsonHeaders(accessToken));
                ResponseEntity<StockEventResponse> createResponse = restTemplate.postForEntity(
                                url("/api/v1/stock-events"),
                                createEntity,
                                StockEventResponse.class);

                assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
                StockEventResponse createdEvent = createResponse.getBody();
                assertThat(createdEvent).isNotNull();
                assertThat(createdEvent.getType()).isEqualTo(StockEventType.OUTBOUND);
                assertThat(createdEvent.getReasonCode()).isEqualTo(StockReasonCode.SALE);
        }

        @Test
        @SuppressWarnings("null")
        void shouldCreateAdjustmentStockEventSuccessfully() {
                String accessToken = authenticateTestUser();

                String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);

                UUID brandId = createTestBrand(accessToken, "Test Brand " + uniqueSuffix);
                UUID categoryId = createTestCategory(accessToken, "Test Category " + uniqueSuffix);
                UUID productId = createTestProduct(accessToken, "Test Product " + uniqueSuffix, brandId, categoryId);
                UUID variantId = createTestVariant(accessToken, productId, "SKU-" + uniqueSuffix);
                UUID warehouseId = createTestWarehouse(accessToken, "WH-" + uniqueSuffix);

                // Create initial stock
                createInboundEvent(accessToken, warehouseId, variantId, 100L);

                // Create ADJUST stock event (can be positive or negative)
                List<CreateStockEventLineRequest> lines = new ArrayList<>();
                lines.add(new CreateStockEventLineRequest(variantId, -10L));

                CreateStockEventRequest createRequest = new CreateStockEventRequest(
                                StockEventType.ADJUST,
                                warehouseId,
                                OffsetDateTime.now(),
                                StockReasonCode.DAMAGE,
                                "Damaged items removed from stock",
                                lines);

                HttpEntity<CreateStockEventRequest> createEntity = new HttpEntity<>(createRequest,
                                authorizedJsonHeaders(accessToken));
                ResponseEntity<StockEventResponse> createResponse = restTemplate.postForEntity(
                                url("/api/v1/stock-events"),
                                createEntity,
                                StockEventResponse.class);

                assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
                StockEventResponse createdEvent = createResponse.getBody();
                assertThat(createdEvent).isNotNull();
                assertThat(createdEvent.getType()).isEqualTo(StockEventType.ADJUST);
                assertThat(createdEvent.getReasonCode()).isEqualTo(StockReasonCode.DAMAGE);
                assertThat(createdEvent.getLines().get(0).getQuantity()).isEqualTo(-10L);
        }

        @Test
        @SuppressWarnings("null")
        void shouldGetStockEventByIdSuccessfully() {
                String accessToken = authenticateTestUser();
                String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);

                UUID brandId = createTestBrand(accessToken, "Test Brand " + uniqueSuffix);
                UUID categoryId = createTestCategory(accessToken, "Test Category " + uniqueSuffix);
                UUID productId = createTestProduct(accessToken, "Test Product " + uniqueSuffix, brandId, categoryId);
                UUID variantId = createTestVariant(accessToken, productId, "SKU-" + uniqueSuffix);
                UUID warehouseId = createTestWarehouse(accessToken, "WH-" + uniqueSuffix);

                // Create event and get ID
                UUID eventId = createInboundEvent(accessToken, warehouseId, variantId, 150L);
                assertThat(eventId).isNotNull();

                System.out.println("=== DEBUG INFO ===");
                System.out.println("Event ID: " + eventId);
                System.out.println("Request URL: " + url("/api/v1/stock-events/" + eventId));

                // Wait for transaction commit
                try {
                        Thread.sleep(200);
                } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                }

                // Try to get as String first to see what we're receiving
                try {
                        ResponseEntity<String> stringResponse = restTemplate.exchange(
                                        url("/api/v1/stock-events/" + eventId),
                                        HttpMethod.GET,
                                        new HttpEntity<>(authorizedHeaders(accessToken)),
                                        String.class);

                        System.out.println("Response Status: " + stringResponse.getStatusCode());
                        System.out.println("Response Body: " + stringResponse.getBody());

                        // Assert the response is OK
                        assertThat(stringResponse.getStatusCode())
                                        .withFailMessage("Expected 200 OK but got " + stringResponse.getStatusCode() +
                                                        ". Response body: " + stringResponse.getBody())
                                        .isEqualTo(HttpStatus.OK);

                        // If OK, try with DTO
                        ResponseEntity<StockEventResponse> dtoResponse = restTemplate.exchange(
                                        url("/api/v1/stock-events/" + eventId),
                                        HttpMethod.GET,
                                        new HttpEntity<>(authorizedHeaders(accessToken)),
                                        StockEventResponse.class);

                        assertThat(dtoResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
                        StockEventResponse fetchedEvent = dtoResponse.getBody();
                        assertThat(fetchedEvent).isNotNull();
                        assertThat(fetchedEvent.getId()).isEqualTo(eventId);
                        assertThat(fetchedEvent.getType()).isEqualTo(StockEventType.INBOUND);
                        assertThat(fetchedEvent.getWarehouseId()).isEqualTo(warehouseId);
                        assertThat(fetchedEvent.getLines()).hasSize(1);

                        System.out.println("=== TEST PASSED ===");
                } catch (Exception e) {
                        System.err.println("=== ERROR OCCURRED ===");
                        System.err.println("Exception: " + e.getClass().getName());
                        System.err.println("Message: " + e.getMessage());
                        throw e;
                }
        }

        @Test
        @SuppressWarnings("null")
        void shouldListStockEventsWithPaginationSuccessfully() {
                String accessToken = authenticateTestUser();

                String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);

                UUID brandId = createTestBrand(accessToken, "Test Brand " + uniqueSuffix);
                UUID categoryId = createTestCategory(accessToken, "Test Category " + uniqueSuffix);
                UUID productId = createTestProduct(accessToken, "Test Product " + uniqueSuffix, brandId, categoryId);
                UUID variantId = createTestVariant(accessToken, productId, "SKU-" + uniqueSuffix);
                UUID warehouseId = createTestWarehouse(accessToken, "WH-" + uniqueSuffix);

                // Create multiple events
                createInboundEvent(accessToken, warehouseId, variantId, 100L);
                createInboundEvent(accessToken, warehouseId, variantId, 50L);

                // List all events with pagination
                ResponseEntity<Map<String, Object>> listResponse = restTemplate.exchange(
                                url("/api/v1/stock-events?page=0&size=10"),
                                HttpMethod.GET,
                                new HttpEntity<>(authorizedHeaders(accessToken)),
                                new ParameterizedTypeReference<Map<String, Object>>() {
                                });

                assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
                Map<String, Object> eventsPage = listResponse.getBody();
                assertThat(eventsPage).isNotNull();
                assertThat(eventsPage).containsKeys("content", "totalElements", "totalPages", "size", "number");
        }

        @Test
        @SuppressWarnings("null")
        void shouldFilterStockEventsByTypeSuccessfully() {
                String accessToken = authenticateTestUser();

                String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);

                UUID brandId = createTestBrand(accessToken, "Test Brand " + uniqueSuffix);
                UUID categoryId = createTestCategory(accessToken, "Test Category " + uniqueSuffix);
                UUID productId = createTestProduct(accessToken, "Test Product " + uniqueSuffix, brandId, categoryId);
                UUID variantId = createTestVariant(accessToken, productId, "SKU-" + uniqueSuffix);
                UUID warehouseId = createTestWarehouse(accessToken, "WH-" + uniqueSuffix);

                // Create INBOUND events
                createInboundEvent(accessToken, warehouseId, variantId, 200L);
                createInboundEvent(accessToken, warehouseId, variantId, 100L);

                // Create OUTBOUND event
                List<CreateStockEventLineRequest> outboundLines = new ArrayList<>();
                outboundLines.add(new CreateStockEventLineRequest(variantId, 50L));

                CreateStockEventRequest outboundRequest = new CreateStockEventRequest(
                                StockEventType.OUTBOUND,
                                warehouseId,
                                OffsetDateTime.now(),
                                StockReasonCode.SALE,
                                null,
                                outboundLines);

                restTemplate.postForEntity(
                                url("/api/v1/stock-events"),
                                new HttpEntity<>(outboundRequest, authorizedJsonHeaders(accessToken)),
                                StockEventResponse.class);

                // Filter by INBOUND type
                ResponseEntity<Map<String, Object>> inboundResponse = restTemplate.exchange(
                                url("/api/v1/stock-events?type=INBOUND"),
                                HttpMethod.GET,
                                new HttpEntity<>(authorizedHeaders(accessToken)),
                                new ParameterizedTypeReference<Map<String, Object>>() {
                                });

                assertThat(inboundResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
                Map<String, Object> inboundPage = inboundResponse.getBody();
                assertThat(inboundPage).isNotNull();

                // Filter by OUTBOUND type
                ResponseEntity<Map<String, Object>> outboundResponse = restTemplate.exchange(
                                url("/api/v1/stock-events?type=OUTBOUND"),
                                HttpMethod.GET,
                                new HttpEntity<>(authorizedHeaders(accessToken)),
                                new ParameterizedTypeReference<Map<String, Object>>() {
                                });

                assertThat(outboundResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
                Map<String, Object> outboundPage = outboundResponse.getBody();
                assertThat(outboundPage).isNotNull();
        }

        @Test
        @SuppressWarnings("null")
        void shouldFilterStockEventsByWarehouseSuccessfully() {
                String accessToken = authenticateTestUser();

                String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);

                UUID brandId = createTestBrand(accessToken, "Test Brand " + uniqueSuffix);
                UUID categoryId = createTestCategory(accessToken, "Test Category " + uniqueSuffix);
                UUID productId = createTestProduct(accessToken, "Test Product " + uniqueSuffix, brandId, categoryId);
                UUID variantId = createTestVariant(accessToken, productId, "SKU-" + uniqueSuffix);
                UUID warehouse1Id = createTestWarehouse(accessToken, "WH1-" + uniqueSuffix);
                UUID warehouse2Id = createTestWarehouse(accessToken, "WH2-" + uniqueSuffix);

                // Create events in different warehouses
                createInboundEvent(accessToken, warehouse1Id, variantId, 100L);
                createInboundEvent(accessToken, warehouse2Id, variantId, 200L);

                // Filter by warehouse1
                ResponseEntity<Map<String, Object>> wh1Response = restTemplate.exchange(
                                url("/api/v1/stock-events?warehouseId=" + warehouse1Id),
                                HttpMethod.GET,
                                new HttpEntity<>(authorizedHeaders(accessToken)),
                                new ParameterizedTypeReference<Map<String, Object>>() {
                                });

                assertThat(wh1Response.getStatusCode()).isEqualTo(HttpStatus.OK);
                Map<String, Object> wh1Page = wh1Response.getBody();
                assertThat(wh1Page).isNotNull();
        }

        @Test
        @SuppressWarnings("null")
        void shouldFilterStockEventsByVariantSuccessfully() {
                String accessToken = authenticateTestUser();

                String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);

                UUID brandId = createTestBrand(accessToken, "Test Brand " + uniqueSuffix);
                UUID categoryId = createTestCategory(accessToken, "Test Category " + uniqueSuffix);
                UUID productId = createTestProduct(accessToken, "Test Product " + uniqueSuffix, brandId, categoryId);
                UUID variant1Id = createTestVariant(accessToken, productId, "SKU1-" + uniqueSuffix);
                UUID variant2Id = createTestVariant(accessToken, productId, "SKU2-" + uniqueSuffix);
                UUID warehouseId = createTestWarehouse(accessToken, "WH-" + uniqueSuffix);

                // Create events for different variants
                createInboundEvent(accessToken, warehouseId, variant1Id, 100L);
                createInboundEvent(accessToken, warehouseId, variant2Id, 200L);

                // Filter by variant1
                ResponseEntity<Map<String, Object>> variant1Response = restTemplate.exchange(
                                url("/api/v1/stock-events?variantId=" + variant1Id),
                                HttpMethod.GET,
                                new HttpEntity<>(authorizedHeaders(accessToken)),
                                new ParameterizedTypeReference<Map<String, Object>>() {
                                });

                assertThat(variant1Response.getStatusCode()).isEqualTo(HttpStatus.OK);
                Map<String, Object> variant1Page = variant1Response.getBody();
                assertThat(variant1Page).isNotNull();
        }

        @Test
        @SuppressWarnings("null")
        void shouldUseIdempotencyKeySuccessfully() {
                String accessToken = authenticateTestUser();

                String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);

                UUID brandId = createTestBrand(accessToken, "Test Brand " + uniqueSuffix);
                UUID categoryId = createTestCategory(accessToken, "Test Category " + uniqueSuffix);
                UUID productId = createTestProduct(accessToken, "Test Product " + uniqueSuffix, brandId, categoryId);
                UUID variantId = createTestVariant(accessToken, productId, "SKU-" + uniqueSuffix);
                UUID warehouseId = createTestWarehouse(accessToken, "WH-" + uniqueSuffix);

                String idempotencyKey = "idempotency-key-" + uniqueSuffix;

                List<CreateStockEventLineRequest> lines = new ArrayList<>();
                lines.add(new CreateStockEventLineRequest(variantId, 100L));

                CreateStockEventRequest createRequest = new CreateStockEventRequest(
                                StockEventType.INBOUND,
                                warehouseId,
                                OffsetDateTime.now(),
                                StockReasonCode.PURCHASE,
                                "Idempotent request",
                                lines);

                HttpHeaders headersWithIdempotency = authorizedJsonHeaders(accessToken);
                headersWithIdempotency.set("Idempotency-Key", idempotencyKey);

                HttpEntity<CreateStockEventRequest> createEntity = new HttpEntity<>(createRequest,
                                headersWithIdempotency);

                // First request
                ResponseEntity<StockEventResponse> firstResponse = restTemplate.postForEntity(
                                url("/api/v1/stock-events"),
                                createEntity,
                                StockEventResponse.class);

                assertThat(firstResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
                StockEventResponse firstEvent = firstResponse.getBody();
                assertThat(firstEvent).isNotNull();

                // Second request with same idempotency key
                ResponseEntity<StockEventResponse> secondResponse = restTemplate.postForEntity(
                                url("/api/v1/stock-events"),
                                createEntity,
                                StockEventResponse.class);

                assertThat(secondResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
                StockEventResponse secondEvent = secondResponse.getBody();
                assertThat(secondEvent).isNotNull();
                assertThat(secondEvent.getId()).isEqualTo(firstEvent.getId());
        }

        private String authenticateTestUser() {
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.setAccept(List.of(MediaType.APPLICATION_JSON));

                HttpEntity<LoginRequest> loginEntity = new HttpEntity<>(
                                new LoginRequest(TEST_USERNAME, TEST_PASSWORD),
                                headers);

                ResponseEntity<LoginResponse> response = restTemplate.postForEntity(
                                url("/api/v1/auth/login"),
                                loginEntity,
                                LoginResponse.class);

                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                LoginResponse body = response.getBody();
                assertThat(body).isNotNull();
                assertThat(body.getAccessToken()).isNotBlank();
                return body.getAccessToken();
        }

        private UUID createTestBrand(String accessToken, String brandName) {
                CreateBrandRequest brandRequest = new CreateBrandRequest(brandName, "Test brand description");
                HttpEntity<CreateBrandRequest> entity = new HttpEntity<>(brandRequest,
                                authorizedJsonHeaders(accessToken));

                ResponseEntity<BrandResponse> response = restTemplate.postForEntity(
                                url("/api/v1/brands"),
                                entity,
                                BrandResponse.class);

                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
                BrandResponse brand = response.getBody();
                assertThat(brand).isNotNull();
                return brand.getId();
        }

        private UUID createTestCategory(String accessToken, String categoryName) {
                CreateCategoryRequest categoryRequest = new CreateCategoryRequest(categoryName, "Test category", null);
                HttpEntity<CreateCategoryRequest> entity = new HttpEntity<>(categoryRequest,
                                authorizedJsonHeaders(accessToken));

                ResponseEntity<CategoryResponse> response = restTemplate.postForEntity(
                                url("/api/v1/categories"),
                                entity,
                                CategoryResponse.class);

                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
                CategoryResponse category = response.getBody();
                assertThat(category).isNotNull();
                return category.getId();
        }

        private UUID createTestProduct(String accessToken, String productName, UUID brandId, UUID categoryId) {
                CreateProductRequest productRequest = new CreateProductRequest(
                                productName,
                                "Test product description",
                                brandId,
                                categoryId,
                                10000L,
                                LocalDate.now().plusMonths(6));
                HttpEntity<CreateProductRequest> entity = new HttpEntity<>(productRequest,
                                authorizedJsonHeaders(accessToken));

                ResponseEntity<ProductResponse> response = restTemplate.postForEntity(
                                url("/api/v1/products"),
                                entity,
                                ProductResponse.class);

                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
                ProductResponse product = response.getBody();
                assertThat(product).isNotNull();
                return product.getId();
        }

        private UUID createTestVariant(String accessToken, UUID productId, String sku) {
                UUID colorDefId = createTestAttributeDefinition(accessToken, "Color-" + sku, "COLOR-" + sku,
                                AttributeType.ENUM);
                UUID redValueId = createTestAttributeValue(accessToken, colorDefId, "Red", "RED");

                List<CreateProductVariantRequest.VariantAttributePair> attributes = new ArrayList<>();
                attributes.add(new CreateProductVariantRequest.VariantAttributePair(colorDefId, redValueId));

                CreateProductVariantRequest variantRequest = new CreateProductVariantRequest(
                                sku,
                                "GTIN-" + sku,
                                attributes,
                                12000L,
                                500,
                                10,
                                5,
                                15);

                HttpEntity<CreateProductVariantRequest> entity = new HttpEntity<>(variantRequest,
                                authorizedJsonHeaders(accessToken));

                ResponseEntity<ProductVariantResponse> response = restTemplate.postForEntity(
                                url("/api/v1/products/" + productId + "/variants"),
                                entity,
                                ProductVariantResponse.class);

                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
                ProductVariantResponse variant = response.getBody();
                assertThat(variant).isNotNull();
                return variant.getId();
        }

        private UUID createTestAttributeDefinition(String accessToken, String name, String code, AttributeType type) {
                CreateAttributeDefinitionRequest defRequest = new CreateAttributeDefinitionRequest();
                defRequest.setName(name);
                defRequest.setCode(code);
                defRequest.setType(type);
                defRequest.setIsVariantDefining(true);

                HttpEntity<CreateAttributeDefinitionRequest> entity = new HttpEntity<>(defRequest,
                                authorizedJsonHeaders(accessToken));

                ResponseEntity<AttributeDefinitionResponse> response = restTemplate.postForEntity(
                                url("/api/v1/attributes/definitions"),
                                entity,
                                AttributeDefinitionResponse.class);

                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
                AttributeDefinitionResponse definition = response.getBody();
                assertThat(definition).isNotNull();
                return definition.getId();
        }

        private UUID createTestAttributeValue(String accessToken, UUID definitionId, String value, String code) {
                CreateAttributeValueRequest valueRequest = new CreateAttributeValueRequest(value, code, null, null);
                HttpEntity<CreateAttributeValueRequest> entity = new HttpEntity<>(valueRequest,
                                authorizedJsonHeaders(accessToken));

                ResponseEntity<AttributeValueResponse> response = restTemplate.postForEntity(
                                url("/api/v1/attributes/definitions/" + definitionId + "/values"),
                                entity,
                                AttributeValueResponse.class);

                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
                AttributeValueResponse attributeValue = response.getBody();
                assertThat(attributeValue).isNotNull();
                return attributeValue.getId();
        }

        private UUID createTestWarehouse(String accessToken, String code) {
                CreateWarehouseRequest warehouseRequest = new CreateWarehouseRequest(
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
                                null);
                HttpEntity<CreateWarehouseRequest> entity = new HttpEntity<>(warehouseRequest,
                                authorizedJsonHeaders(accessToken));

                ResponseEntity<WarehouseResponse> response = restTemplate.postForEntity(
                                url("/api/v1/warehouses"),
                                entity,
                                WarehouseResponse.class);

                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
                WarehouseResponse warehouse = response.getBody();
                assertThat(warehouse).isNotNull();
                return warehouse.getId();
        }

        private UUID createInboundEvent(String accessToken, UUID warehouseId, UUID variantId, Long quantity) {
                List<CreateStockEventLineRequest> lines = new ArrayList<>();
                lines.add(new CreateStockEventLineRequest(variantId, quantity));

                CreateStockEventRequest createRequest = new CreateStockEventRequest(
                                StockEventType.INBOUND,
                                warehouseId,
                                OffsetDateTime.now(),
                                StockReasonCode.PURCHASE,
                                null,
                                lines);

                HttpEntity<CreateStockEventRequest> createEntity = new HttpEntity<>(createRequest,
                                authorizedJsonHeaders(accessToken));
                ResponseEntity<StockEventResponse> createResponse = restTemplate.postForEntity(
                                url("/api/v1/stock-events"),
                                createEntity,
                                StockEventResponse.class);

                assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
                StockEventResponse createdEvent = createResponse.getBody();
                assertThat(createdEvent).isNotNull();
                return createdEvent.getId();
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
