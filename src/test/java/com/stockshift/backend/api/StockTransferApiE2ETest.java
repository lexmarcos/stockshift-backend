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
import com.stockshift.backend.api.dto.transfer.CreateTransferLineRequest;
import com.stockshift.backend.api.dto.transfer.CreateTransferRequest;
import com.stockshift.backend.api.dto.transfer.TransferResponse;
import com.stockshift.backend.api.dto.variant.CreateProductVariantRequest;
import com.stockshift.backend.api.dto.variant.ProductVariantResponse;
import com.stockshift.backend.api.dto.warehouse.CreateWarehouseRequest;
import com.stockshift.backend.api.dto.warehouse.WarehouseResponse;
import com.stockshift.backend.domain.attribute.AttributeType;
import com.stockshift.backend.domain.stock.StockEventType;
import com.stockshift.backend.domain.stock.StockReasonCode;
import com.stockshift.backend.domain.stock.TransferStatus;
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
        "spring.datasource.url=jdbc:h2:mem:stockshift-transfer-test-db;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "spring.jpa.properties.hibernate.globally_quoted_identifiers=true"
})
class StockTransferApiE2ETest {

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
    void shouldCreateDraftTransferSuccessfully() {
        String accessToken = authenticateTestUser();
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);

        // Create test data
        UUID brandId = createTestBrand(accessToken, "Brand " + uniqueSuffix);
        UUID categoryId = createTestCategory(accessToken, "Category " + uniqueSuffix);
        UUID productId = createTestProduct(accessToken, "Product " + uniqueSuffix, brandId, categoryId);
        UUID variantId = createTestVariant(accessToken, productId, "SKU-" + uniqueSuffix);
        UUID originWarehouseId = createTestWarehouse(accessToken, "ORIGIN-" + uniqueSuffix);
        UUID destinationWarehouseId = createTestWarehouse(accessToken, "DEST-" + uniqueSuffix);

        // Create initial stock at origin
        createInboundEvent(accessToken, originWarehouseId, variantId, 100L);

        // Create transfer request
        List<CreateTransferLineRequest> lines = new ArrayList<>();
        lines.add(new CreateTransferLineRequest(variantId, 50L));

        CreateTransferRequest createRequest = new CreateTransferRequest(
                originWarehouseId,
                destinationWarehouseId,
                OffsetDateTime.now(),
                "Transfer test notes",
                lines
        );

        HttpEntity<CreateTransferRequest> createEntity = new HttpEntity<>(createRequest, authorizedJsonHeaders(accessToken));
        ResponseEntity<TransferResponse> createResponse = restTemplate.postForEntity(
                url("/api/v1/stock-transfers"),
                createEntity,
                TransferResponse.class
        );

        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        TransferResponse transfer = createResponse.getBody();
        assertThat(transfer).isNotNull();
        assertThat(transfer.getId()).isNotNull();
        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.DRAFT);
        assertThat(transfer.getOriginWarehouseId()).isEqualTo(originWarehouseId);
        assertThat(transfer.getDestinationWarehouseId()).isEqualTo(destinationWarehouseId);
        assertThat(transfer.getNotes()).isEqualTo("Transfer test notes");
        assertThat(transfer.getLines()).hasSize(1);
        assertThat(transfer.getLines().get(0).getVariantId()).isEqualTo(variantId);
        assertThat(transfer.getLines().get(0).getQuantity()).isEqualTo(50L);
        assertThat(transfer.getCreatedById()).isNotNull();
        assertThat(transfer.getCreatedAt()).isNotNull();
        assertThat(transfer.getConfirmedById()).isNull();
        assertThat(transfer.getOutboundEventId()).isNull();
        assertThat(transfer.getInboundEventId()).isNull();
    }

    @Test
    @SuppressWarnings("null")
    void shouldRejectSameOriginAndDestination() {
        String accessToken = authenticateTestUser();
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);

        UUID brandId = createTestBrand(accessToken, "Brand " + uniqueSuffix);
        UUID categoryId = createTestCategory(accessToken, "Category " + uniqueSuffix);
        UUID productId = createTestProduct(accessToken, "Product " + uniqueSuffix, brandId, categoryId);
        UUID variantId = createTestVariant(accessToken, productId, "SKU-" + uniqueSuffix);
        UUID warehouseId = createTestWarehouse(accessToken, "WH-" + uniqueSuffix);

        List<CreateTransferLineRequest> lines = new ArrayList<>();
        lines.add(new CreateTransferLineRequest(variantId, 50L));

        CreateTransferRequest createRequest = new CreateTransferRequest(
                warehouseId,
                warehouseId, // Same warehouse
                OffsetDateTime.now(),
                null,
                lines
        );

        HttpEntity<CreateTransferRequest> createEntity = new HttpEntity<>(createRequest, authorizedJsonHeaders(accessToken));
        ResponseEntity<Map<String, Object>> createResponse = restTemplate.exchange(
                url("/api/v1/stock-transfers"),
                HttpMethod.POST,
                createEntity,
                new ParameterizedTypeReference<Map<String, Object>>() {}
        );

        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @SuppressWarnings("null")
    void shouldConfirmTransferSuccessfully() {
        String accessToken = authenticateTestUser();
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);

        UUID brandId = createTestBrand(accessToken, "Brand " + uniqueSuffix);
        UUID categoryId = createTestCategory(accessToken, "Category " + uniqueSuffix);
        UUID productId = createTestProduct(accessToken, "Product " + uniqueSuffix, brandId, categoryId);
        UUID variantId = createTestVariant(accessToken, productId, "SKU-" + uniqueSuffix);
        UUID originWarehouseId = createTestWarehouse(accessToken, "ORIGIN-" + uniqueSuffix);
        UUID destinationWarehouseId = createTestWarehouse(accessToken, "DEST-" + uniqueSuffix);

        // Create initial stock
        createInboundEvent(accessToken, originWarehouseId, variantId, 100L);

        // Create draft transfer
        UUID transferId = createDraftTransfer(accessToken, originWarehouseId, destinationWarehouseId, variantId, 50L);

        // Confirm transfer
        HttpHeaders confirmHeaders = authorizedJsonHeaders(accessToken);
        confirmHeaders.set("Idempotency-Key", "confirm-key-" + uniqueSuffix);

        ResponseEntity<TransferResponse> confirmResponse = restTemplate.exchange(
                url("/api/v1/stock-transfers/" + transferId + "/confirm"),
                HttpMethod.POST,
                new HttpEntity<>(confirmHeaders),
                TransferResponse.class
        );

        assertThat(confirmResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        TransferResponse confirmedTransfer = confirmResponse.getBody();
        assertThat(confirmedTransfer).isNotNull();
        assertThat(confirmedTransfer.getStatus()).isEqualTo(TransferStatus.CONFIRMED);
        assertThat(confirmedTransfer.getConfirmedById()).isNotNull();
        assertThat(confirmedTransfer.getConfirmedAt()).isNotNull();
        assertThat(confirmedTransfer.getOutboundEventId()).isNotNull();
        assertThat(confirmedTransfer.getInboundEventId()).isNotNull();
    }

    @Test
    @SuppressWarnings("null")
    void shouldHandleIdempotencyOnConfirm() {
        String accessToken = authenticateTestUser();
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);

        UUID brandId = createTestBrand(accessToken, "Brand " + uniqueSuffix);
        UUID categoryId = createTestCategory(accessToken, "Category " + uniqueSuffix);
        UUID productId = createTestProduct(accessToken, "Product " + uniqueSuffix, brandId, categoryId);
        UUID variantId = createTestVariant(accessToken, productId, "SKU-" + uniqueSuffix);
        UUID originWarehouseId = createTestWarehouse(accessToken, "ORIGIN-" + uniqueSuffix);
        UUID destinationWarehouseId = createTestWarehouse(accessToken, "DEST-" + uniqueSuffix);

        createInboundEvent(accessToken, originWarehouseId, variantId, 100L);
        UUID transferId = createDraftTransfer(accessToken, originWarehouseId, destinationWarehouseId, variantId, 50L);

        String idempotencyKey = "idempotency-" + uniqueSuffix;
        HttpHeaders confirmHeaders = authorizedJsonHeaders(accessToken);
        confirmHeaders.set("Idempotency-Key", idempotencyKey);

        // First confirm
        ResponseEntity<TransferResponse> firstResponse = restTemplate.exchange(
                url("/api/v1/stock-transfers/" + transferId + "/confirm"),
                HttpMethod.POST,
                new HttpEntity<>(confirmHeaders),
                TransferResponse.class
        );

        assertThat(firstResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        TransferResponse firstTransfer = firstResponse.getBody();
        assertThat(firstTransfer).isNotNull();

        // Second confirm with same key
        ResponseEntity<TransferResponse> secondResponse = restTemplate.exchange(
                url("/api/v1/stock-transfers/" + transferId + "/confirm"),
                HttpMethod.POST,
                new HttpEntity<>(confirmHeaders),
                TransferResponse.class
        );

        assertThat(secondResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        TransferResponse secondTransfer = secondResponse.getBody();
        assertThat(secondTransfer).isNotNull();
        assertThat(secondTransfer.getId()).isEqualTo(firstTransfer.getId());
        assertThat(secondTransfer.getOutboundEventId()).isEqualTo(firstTransfer.getOutboundEventId());
        assertThat(secondTransfer.getInboundEventId()).isEqualTo(firstTransfer.getInboundEventId());
    }

    @Test
    @SuppressWarnings("null")
    void shouldRejectIdempotencyConflict() {
        String accessToken = authenticateTestUser();
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);

        UUID brandId = createTestBrand(accessToken, "Brand " + uniqueSuffix);
        UUID categoryId = createTestCategory(accessToken, "Category " + uniqueSuffix);
        UUID productId = createTestProduct(accessToken, "Product " + uniqueSuffix, brandId, categoryId);
        UUID variantId = createTestVariant(accessToken, productId, "SKU-" + uniqueSuffix);
        UUID originWarehouseId = createTestWarehouse(accessToken, "ORIGIN-" + uniqueSuffix);
        UUID destinationWarehouseId = createTestWarehouse(accessToken, "DEST-" + uniqueSuffix);

        createInboundEvent(accessToken, originWarehouseId, variantId, 200L);

        UUID transferId1 = createDraftTransfer(accessToken, originWarehouseId, destinationWarehouseId, variantId, 50L);
        UUID transferId2 = createDraftTransfer(accessToken, originWarehouseId, destinationWarehouseId, variantId, 30L);

        String idempotencyKey = "conflict-key-" + uniqueSuffix;
        HttpHeaders confirmHeaders = authorizedJsonHeaders(accessToken);
        confirmHeaders.set("Idempotency-Key", idempotencyKey);

        // Confirm first transfer
        restTemplate.exchange(
                url("/api/v1/stock-transfers/" + transferId1 + "/confirm"),
                HttpMethod.POST,
                new HttpEntity<>(confirmHeaders),
                TransferResponse.class
        );

        // Try to confirm second transfer with same key
        ResponseEntity<Map<String, Object>> conflictResponse = restTemplate.exchange(
                url("/api/v1/stock-transfers/" + transferId2 + "/confirm"),
                HttpMethod.POST,
                new HttpEntity<>(confirmHeaders),
                new ParameterizedTypeReference<Map<String, Object>>() {}
        );

        assertThat(conflictResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @SuppressWarnings("null")
    void shouldRejectConfirmWhenNotDraft() {
        String accessToken = authenticateTestUser();
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);

        UUID brandId = createTestBrand(accessToken, "Brand " + uniqueSuffix);
        UUID categoryId = createTestCategory(accessToken, "Category " + uniqueSuffix);
        UUID productId = createTestProduct(accessToken, "Product " + uniqueSuffix, brandId, categoryId);
        UUID variantId = createTestVariant(accessToken, productId, "SKU-" + uniqueSuffix);
        UUID originWarehouseId = createTestWarehouse(accessToken, "ORIGIN-" + uniqueSuffix);
        UUID destinationWarehouseId = createTestWarehouse(accessToken, "DEST-" + uniqueSuffix);

        createInboundEvent(accessToken, originWarehouseId, variantId, 100L);
        UUID transferId = createDraftTransfer(accessToken, originWarehouseId, destinationWarehouseId, variantId, 50L);

        // Confirm once
        HttpHeaders confirmHeaders = authorizedJsonHeaders(accessToken);
        confirmHeaders.set("Idempotency-Key", "first-confirm-" + uniqueSuffix);
        restTemplate.exchange(
                url("/api/v1/stock-transfers/" + transferId + "/confirm"),
                HttpMethod.POST,
                new HttpEntity<>(confirmHeaders),
                TransferResponse.class
        );

        // Try to confirm again with different key
        HttpHeaders secondConfirmHeaders = authorizedJsonHeaders(accessToken);
        secondConfirmHeaders.set("Idempotency-Key", "second-confirm-" + uniqueSuffix);
        ResponseEntity<Map<String, Object>> secondConfirmResponse = restTemplate.exchange(
                url("/api/v1/stock-transfers/" + transferId + "/confirm"),
                HttpMethod.POST,
                new HttpEntity<>(secondConfirmHeaders),
                new ParameterizedTypeReference<Map<String, Object>>() {}
        );

        assertThat(secondConfirmResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @SuppressWarnings("null")
    void shouldCancelDraftSuccessfully() {
        String accessToken = authenticateTestUser();
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);

        UUID brandId = createTestBrand(accessToken, "Brand " + uniqueSuffix);
        UUID categoryId = createTestCategory(accessToken, "Category " + uniqueSuffix);
        UUID productId = createTestProduct(accessToken, "Product " + uniqueSuffix, brandId, categoryId);
        UUID variantId = createTestVariant(accessToken, productId, "SKU-" + uniqueSuffix);
        UUID originWarehouseId = createTestWarehouse(accessToken, "ORIGIN-" + uniqueSuffix);
        UUID destinationWarehouseId = createTestWarehouse(accessToken, "DEST-" + uniqueSuffix);

        UUID transferId = createDraftTransfer(accessToken, originWarehouseId, destinationWarehouseId, variantId, 50L);

        // Cancel draft
        ResponseEntity<TransferResponse> cancelResponse = restTemplate.exchange(
                url("/api/v1/stock-transfers/" + transferId + "/cancel"),
                HttpMethod.POST,
                new HttpEntity<>(authorizedJsonHeaders(accessToken)),
                TransferResponse.class
        );

        assertThat(cancelResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        TransferResponse canceledTransfer = cancelResponse.getBody();
        assertThat(canceledTransfer).isNotNull();
        assertThat(canceledTransfer.getStatus()).isEqualTo(TransferStatus.CANCELED);
    }

    @Test
    @SuppressWarnings("null")
    void shouldRejectCancelWhenNotDraft() {
        String accessToken = authenticateTestUser();
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);

        UUID brandId = createTestBrand(accessToken, "Brand " + uniqueSuffix);
        UUID categoryId = createTestCategory(accessToken, "Category " + uniqueSuffix);
        UUID productId = createTestProduct(accessToken, "Product " + uniqueSuffix, brandId, categoryId);
        UUID variantId = createTestVariant(accessToken, productId, "SKU-" + uniqueSuffix);
        UUID originWarehouseId = createTestWarehouse(accessToken, "ORIGIN-" + uniqueSuffix);
        UUID destinationWarehouseId = createTestWarehouse(accessToken, "DEST-" + uniqueSuffix);

        createInboundEvent(accessToken, originWarehouseId, variantId, 100L);
        UUID transferId = createDraftTransfer(accessToken, originWarehouseId, destinationWarehouseId, variantId, 50L);

        // Confirm transfer
        HttpHeaders confirmHeaders = authorizedJsonHeaders(accessToken);
        confirmHeaders.set("Idempotency-Key", "confirm-" + uniqueSuffix);
        restTemplate.exchange(
                url("/api/v1/stock-transfers/" + transferId + "/confirm"),
                HttpMethod.POST,
                new HttpEntity<>(confirmHeaders),
                TransferResponse.class
        );

        // Try to cancel confirmed transfer
        ResponseEntity<Map<String, Object>> cancelResponse = restTemplate.exchange(
                url("/api/v1/stock-transfers/" + transferId + "/cancel"),
                HttpMethod.POST,
                new HttpEntity<>(authorizedJsonHeaders(accessToken)),
                new ParameterizedTypeReference<Map<String, Object>>() {}
        );

        assertThat(cancelResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @SuppressWarnings("null")
    void shouldGetTransferByIdSuccessfully() {
        String accessToken = authenticateTestUser();
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);

        UUID brandId = createTestBrand(accessToken, "Brand " + uniqueSuffix);
        UUID categoryId = createTestCategory(accessToken, "Category " + uniqueSuffix);
        UUID productId = createTestProduct(accessToken, "Product " + uniqueSuffix, brandId, categoryId);
        UUID variantId = createTestVariant(accessToken, productId, "SKU-" + uniqueSuffix);
        UUID originWarehouseId = createTestWarehouse(accessToken, "ORIGIN-" + uniqueSuffix);
        UUID destinationWarehouseId = createTestWarehouse(accessToken, "DEST-" + uniqueSuffix);

        UUID transferId = createDraftTransfer(accessToken, originWarehouseId, destinationWarehouseId, variantId, 50L);

        // Get transfer
        ResponseEntity<TransferResponse> getResponse = restTemplate.exchange(
                url("/api/v1/stock-transfers/" + transferId),
                HttpMethod.GET,
                new HttpEntity<>(authorizedHeaders(accessToken)),
                TransferResponse.class
        );

        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        TransferResponse transfer = getResponse.getBody();
        assertThat(transfer).isNotNull();
        assertThat(transfer.getId()).isEqualTo(transferId);
        assertThat(transfer.getOriginWarehouseId()).isEqualTo(originWarehouseId);
        assertThat(transfer.getDestinationWarehouseId()).isEqualTo(destinationWarehouseId);
        assertThat(transfer.getLines()).hasSize(1);
    }

    @Test
    @SuppressWarnings("null")
    void shouldListTransfersWithPagination() {
        String accessToken = authenticateTestUser();
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);

        UUID brandId = createTestBrand(accessToken, "Brand " + uniqueSuffix);
        UUID categoryId = createTestCategory(accessToken, "Category " + uniqueSuffix);
        UUID productId = createTestProduct(accessToken, "Product " + uniqueSuffix, brandId, categoryId);
        UUID variantId = createTestVariant(accessToken, productId, "SKU-" + uniqueSuffix);
        UUID originWarehouseId = createTestWarehouse(accessToken, "ORIGIN-" + uniqueSuffix);
        UUID destinationWarehouseId = createTestWarehouse(accessToken, "DEST-" + uniqueSuffix);

        // Create multiple transfers
        createDraftTransfer(accessToken, originWarehouseId, destinationWarehouseId, variantId, 50L);
        createDraftTransfer(accessToken, originWarehouseId, destinationWarehouseId, variantId, 30L);

        // List transfers
        ResponseEntity<Map<String, Object>> listResponse = restTemplate.exchange(
                url("/api/v1/stock-transfers?page=0&size=10"),
                HttpMethod.GET,
                new HttpEntity<>(authorizedHeaders(accessToken)),
                new ParameterizedTypeReference<Map<String, Object>>() {}
        );

        assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> transfersPage = listResponse.getBody();
        assertThat(transfersPage).isNotNull();
        assertThat(transfersPage).containsKeys("content", "totalElements", "totalPages", "size", "number");
    }

    @Test
    @SuppressWarnings("null")
    void shouldFilterTransfersByStatus() {
        String accessToken = authenticateTestUser();
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);

        UUID brandId = createTestBrand(accessToken, "Brand " + uniqueSuffix);
        UUID categoryId = createTestCategory(accessToken, "Category " + uniqueSuffix);
        UUID productId = createTestProduct(accessToken, "Product " + uniqueSuffix, brandId, categoryId);
        UUID variantId = createTestVariant(accessToken, productId, "SKU-" + uniqueSuffix);
        UUID originWarehouseId = createTestWarehouse(accessToken, "ORIGIN-" + uniqueSuffix);
        UUID destinationWarehouseId = createTestWarehouse(accessToken, "DEST-" + uniqueSuffix);

        createInboundEvent(accessToken, originWarehouseId, variantId, 100L);

        // Create draft and confirmed transfers
        createDraftTransfer(accessToken, originWarehouseId, destinationWarehouseId, variantId, 20L);

        UUID confirmedTransferId = createDraftTransfer(accessToken, originWarehouseId, destinationWarehouseId, variantId, 30L);
        HttpHeaders confirmHeaders = authorizedJsonHeaders(accessToken);
        confirmHeaders.set("Idempotency-Key", "filter-confirm-" + uniqueSuffix);
        restTemplate.exchange(
                url("/api/v1/stock-transfers/" + confirmedTransferId + "/confirm"),
                HttpMethod.POST,
                new HttpEntity<>(confirmHeaders),
                TransferResponse.class
        );

        // Filter by DRAFT
        ResponseEntity<Map<String, Object>> draftResponse = restTemplate.exchange(
                url("/api/v1/stock-transfers?status=DRAFT"),
                HttpMethod.GET,
                new HttpEntity<>(authorizedHeaders(accessToken)),
                new ParameterizedTypeReference<Map<String, Object>>() {}
        );

        assertThat(draftResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Filter by CONFIRMED
        ResponseEntity<Map<String, Object>> confirmedResponse = restTemplate.exchange(
                url("/api/v1/stock-transfers?status=CONFIRMED"),
                HttpMethod.GET,
                new HttpEntity<>(authorizedHeaders(accessToken)),
                new ParameterizedTypeReference<Map<String, Object>>() {}
        );

        assertThat(confirmedResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @SuppressWarnings("null")
    void shouldFilterTransfersByOriginWarehouse() {
        String accessToken = authenticateTestUser();
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);

        UUID brandId = createTestBrand(accessToken, "Brand " + uniqueSuffix);
        UUID categoryId = createTestCategory(accessToken, "Category " + uniqueSuffix);
        UUID productId = createTestProduct(accessToken, "Product " + uniqueSuffix, brandId, categoryId);
        UUID variantId = createTestVariant(accessToken, productId, "SKU-" + uniqueSuffix);
        UUID origin1Id = createTestWarehouse(accessToken, "ORIGIN1-" + uniqueSuffix);
        UUID origin2Id = createTestWarehouse(accessToken, "ORIGIN2-" + uniqueSuffix);
        UUID destinationId = createTestWarehouse(accessToken, "DEST-" + uniqueSuffix);

        createDraftTransfer(accessToken, origin1Id, destinationId, variantId, 50L);
        createDraftTransfer(accessToken, origin2Id, destinationId, variantId, 30L);

        // Filter by origin1
        ResponseEntity<Map<String, Object>> origin1Response = restTemplate.exchange(
                url("/api/v1/stock-transfers?originWarehouseId=" + origin1Id),
                HttpMethod.GET,
                new HttpEntity<>(authorizedHeaders(accessToken)),
                new ParameterizedTypeReference<Map<String, Object>>() {}
        );

        assertThat(origin1Response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @SuppressWarnings("null")
    void shouldFilterTransfersByDestinationWarehouse() {
        String accessToken = authenticateTestUser();
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);

        UUID brandId = createTestBrand(accessToken, "Brand " + uniqueSuffix);
        UUID categoryId = createTestCategory(accessToken, "Category " + uniqueSuffix);
        UUID productId = createTestProduct(accessToken, "Product " + uniqueSuffix, brandId, categoryId);
        UUID variantId = createTestVariant(accessToken, productId, "SKU-" + uniqueSuffix);
        UUID originId = createTestWarehouse(accessToken, "ORIGIN-" + uniqueSuffix);
        UUID dest1Id = createTestWarehouse(accessToken, "DEST1-" + uniqueSuffix);
        UUID dest2Id = createTestWarehouse(accessToken, "DEST2-" + uniqueSuffix);

        createDraftTransfer(accessToken, originId, dest1Id, variantId, 50L);
        createDraftTransfer(accessToken, originId, dest2Id, variantId, 30L);

        // Filter by dest1
        ResponseEntity<Map<String, Object>> dest1Response = restTemplate.exchange(
                url("/api/v1/stock-transfers?destinationWarehouseId=" + dest1Id),
                HttpMethod.GET,
                new HttpEntity<>(authorizedHeaders(accessToken)),
                new ParameterizedTypeReference<Map<String, Object>>() {}
        );

        assertThat(dest1Response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @SuppressWarnings("null")
    void shouldRejectInsufficientStock() {
        String accessToken = authenticateTestUser();
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);

        UUID brandId = createTestBrand(accessToken, "Brand " + uniqueSuffix);
        UUID categoryId = createTestCategory(accessToken, "Category " + uniqueSuffix);
        UUID productId = createTestProduct(accessToken, "Product " + uniqueSuffix, brandId, categoryId);
        UUID variantId = createTestVariant(accessToken, productId, "SKU-" + uniqueSuffix);
        UUID originWarehouseId = createTestWarehouse(accessToken, "ORIGIN-" + uniqueSuffix);
        UUID destinationWarehouseId = createTestWarehouse(accessToken, "DEST-" + uniqueSuffix);

        // Create only 50 units of stock
        createInboundEvent(accessToken, originWarehouseId, variantId, 50L);

        // Try to transfer 100 units (more than available)
        UUID transferId = createDraftTransfer(accessToken, originWarehouseId, destinationWarehouseId, variantId, 100L);

        HttpHeaders confirmHeaders = authorizedJsonHeaders(accessToken);
        confirmHeaders.set("Idempotency-Key", "insufficient-" + uniqueSuffix);

        ResponseEntity<Map<String, Object>> confirmResponse = restTemplate.exchange(
                url("/api/v1/stock-transfers/" + transferId + "/confirm"),
                HttpMethod.POST,
                new HttpEntity<>(confirmHeaders),
                new ParameterizedTypeReference<Map<String, Object>>() {}
        );

        assertThat(confirmResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    private String authenticateTestUser() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        HttpEntity<LoginRequest> loginEntity = new HttpEntity<>(
                new LoginRequest(TEST_USERNAME, TEST_PASSWORD),
                headers
        );

        ResponseEntity<LoginResponse> response = restTemplate.postForEntity(
                url("/api/v1/auth/login"),
                loginEntity,
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

    private UUID createTestProduct(String accessToken, String productName, UUID brandId, UUID categoryId) {
        CreateProductRequest productRequest = new CreateProductRequest(
                productName,
                "Test product description",
                brandId,
                categoryId,
                10000L,
                LocalDate.now().plusMonths(6)
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
        UUID colorDefId = createTestAttributeDefinition(accessToken, "Color-" + sku, "COLOR-" + sku, AttributeType.ENUM);
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
        CreateAttributeDefinitionRequest defRequest = new CreateAttributeDefinitionRequest();
        defRequest.setName(name);
        defRequest.setCode(code);
        defRequest.setType(type);
        defRequest.setIsVariantDefining(true);

        HttpEntity<CreateAttributeDefinitionRequest> entity = new HttpEntity<>(defRequest, authorizedJsonHeaders(accessToken));

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
        CreateAttributeValueRequest valueRequest = new CreateAttributeValueRequest(value, code, null, null);
        HttpEntity<CreateAttributeValueRequest> entity = new HttpEntity<>(valueRequest, authorizedJsonHeaders(accessToken));

        ResponseEntity<AttributeValueResponse> response = restTemplate.postForEntity(
                url("/api/v1/attributes/definitions/" + definitionId + "/values"),
                entity,
                AttributeValueResponse.class
        );

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
                null
        );
        HttpEntity<CreateWarehouseRequest> entity = new HttpEntity<>(warehouseRequest, authorizedJsonHeaders(accessToken));

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

    private UUID createInboundEvent(String accessToken, UUID warehouseId, UUID variantId, Long quantity) {
        List<CreateStockEventLineRequest> lines = new ArrayList<>();
        lines.add(new CreateStockEventLineRequest(variantId, quantity));

        CreateStockEventRequest createRequest = new CreateStockEventRequest(
                StockEventType.INBOUND,
                warehouseId,
                OffsetDateTime.now(),
                StockReasonCode.PURCHASE,
                null,
                lines
        );

        HttpEntity<CreateStockEventRequest> createEntity = new HttpEntity<>(createRequest, authorizedJsonHeaders(accessToken));
        ResponseEntity<StockEventResponse> createResponse = restTemplate.postForEntity(
                url("/api/v1/stock-events"),
                createEntity,
                StockEventResponse.class
        );

        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        StockEventResponse createdEvent = createResponse.getBody();
        assertThat(createdEvent).isNotNull();
        return createdEvent.getId();
    }

    private UUID createDraftTransfer(String accessToken, UUID originId, UUID destinationId, UUID variantId, Long quantity) {
        List<CreateTransferLineRequest> lines = new ArrayList<>();
        lines.add(new CreateTransferLineRequest(variantId, quantity));

        CreateTransferRequest createRequest = new CreateTransferRequest(
                originId,
                destinationId,
                OffsetDateTime.now(),
                null,
                lines
        );

        HttpEntity<CreateTransferRequest> createEntity = new HttpEntity<>(createRequest, authorizedJsonHeaders(accessToken));
        ResponseEntity<TransferResponse> createResponse = restTemplate.postForEntity(
                url("/api/v1/stock-transfers"),
                createEntity,
                TransferResponse.class
        );

        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        TransferResponse transfer = createResponse.getBody();
        assertThat(transfer).isNotNull();
        return transfer.getId();
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
