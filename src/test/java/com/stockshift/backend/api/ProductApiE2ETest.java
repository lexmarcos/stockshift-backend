package com.stockshift.backend.api;

import com.stockshift.backend.api.dto.auth.LoginRequest;
import com.stockshift.backend.api.dto.auth.LoginResponse;
import com.stockshift.backend.api.dto.brand.BrandResponse;
import com.stockshift.backend.api.dto.brand.CreateBrandRequest;
import com.stockshift.backend.api.dto.category.CategoryResponse;
import com.stockshift.backend.api.dto.category.CreateCategoryRequest;
import com.stockshift.backend.api.dto.product.CreateProductRequest;
import com.stockshift.backend.api.dto.product.ProductResponse;
import com.stockshift.backend.api.dto.product.UpdateProductRequest;
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
        "spring.jpa.show-sql=false"
})
class ProductApiE2ETest {

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
    void shouldExecuteProductLifecycleSuccessfully() {
        String accessToken = authenticateTestUser();

        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);

        // Create brand for the product
        UUID brandId = createTestBrand(accessToken, "Test Brand " + uniqueSuffix);

        // Create category for the product
        UUID categoryId = createTestCategory(accessToken, "Test Category " + uniqueSuffix);

        String productName = "E2E Product " + uniqueSuffix;
        String productDescription = "Initial description for " + uniqueSuffix;
        Long basePrice = 10000L;
        LocalDate expiryDate = LocalDate.now().plusMonths(6);

        CreateProductRequest createRequest = new CreateProductRequest(
                productName,
                productDescription,
                brandId,
                categoryId,
                basePrice,
                expiryDate
        );

        HttpEntity<CreateProductRequest> createEntity = new HttpEntity<>(createRequest, authorizedJsonHeaders(accessToken));
        ResponseEntity<ProductResponse> createResponse = restTemplate.postForEntity(
                url("/api/v1/products"),
                createEntity,
                ProductResponse.class
        );

        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        ProductResponse createdProduct = createResponse.getBody();
        assertThat(createdProduct).isNotNull();
        assertThat(createdProduct.getId()).isNotNull();
        assertThat(createdProduct.getName()).isEqualTo(productName);
        assertThat(createdProduct.getDescription()).isEqualTo(productDescription);
        assertThat(createdProduct.getBrandId()).isEqualTo(brandId);
        assertThat(createdProduct.getCategoryId()).isEqualTo(categoryId);
        assertThat(createdProduct.getBasePrice()).isEqualTo(basePrice);
        assertThat(createdProduct.getExpiryDate()).isEqualTo(expiryDate);
        assertThat(createdProduct.getExpired()).isFalse();
        assertThat(createdProduct.getActive()).isTrue();

        UUID productId = createdProduct.getId();

        // Get by ID
        ProductResponse fetchedById = getProductById(accessToken, productId);
        assertThat(fetchedById.getName()).isEqualTo(productName);
        assertThat(fetchedById.getDescription()).isEqualTo(productDescription);
        assertThat(fetchedById.getActive()).isTrue();

        // Get by name
        ProductResponse fetchedByName = getProductByName(accessToken, productName);
        assertThat(fetchedByName.getId()).isEqualTo(productId);

        // Update product
        String updatedName = productName + " Updated";
        String updatedDescription = "Updated description for " + uniqueSuffix;
        Long updatedPrice = 15000L;

        UpdateProductRequest updateRequest = new UpdateProductRequest(
                updatedName,
                updatedDescription,
                brandId,
                categoryId,
                updatedPrice,
                expiryDate
        );

        HttpEntity<UpdateProductRequest> updateEntity = new HttpEntity<>(updateRequest, authorizedJsonHeaders(accessToken));
        ResponseEntity<ProductResponse> updateResponse = restTemplate.exchange(
                url("/api/v1/products/" + productId),
                HttpMethod.PUT,
                updateEntity,
                ProductResponse.class
        );

        assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        ProductResponse updatedProduct = updateResponse.getBody();
        assertThat(updatedProduct).isNotNull();
        assertThat(updatedProduct.getName()).isEqualTo(updatedName);
        assertThat(updatedProduct.getDescription()).isEqualTo(updatedDescription);
        assertThat(updatedProduct.getBasePrice()).isEqualTo(updatedPrice);
        assertThat(updatedProduct.getActive()).isTrue();

        // Delete product (soft delete)
        ResponseEntity<Void> deleteResponse = restTemplate.exchange(
                url("/api/v1/products/" + productId),
                HttpMethod.DELETE,
                new HttpEntity<>(authorizedHeaders(accessToken)),
                Void.class
        );

        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // Verify product is inactive
        ProductResponse inactiveProduct = getProductById(accessToken, productId);
        assertThat(inactiveProduct.getActive()).isFalse();

        // Activate product
        ResponseEntity<ProductResponse> activateResponse = restTemplate.exchange(
                url("/api/v1/products/" + productId + "/activate"),
                HttpMethod.PATCH,
                new HttpEntity<>(authorizedHeaders(accessToken)),
                ProductResponse.class
        );

        assertThat(activateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        ProductResponse reactivatedProduct = activateResponse.getBody();
        assertThat(reactivatedProduct).isNotNull();
        assertThat(reactivatedProduct.getActive()).isTrue();
    }

    @Test
    void shouldGetAllProductsWithPaginationAndFilters() {
        String accessToken = authenticateTestUser();

        // Get all products with pagination
        ResponseEntity<Map<String, Object>> allProductsResponse = restTemplate.exchange(
                url("/api/v1/products?page=0&size=10"),
                HttpMethod.GET,
                new HttpEntity<>(authorizedHeaders(accessToken)),
                new ParameterizedTypeReference<Map<String, Object>>() {}
        );

        assertThat(allProductsResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> productsPage = allProductsResponse.getBody();
        assertThat(productsPage).isNotNull();
        assertThat(productsPage).containsKeys("content", "totalElements", "totalPages", "size", "number");

        // Get only active products
        ResponseEntity<Map<String, Object>> activeProductsResponse = restTemplate.exchange(
                url("/api/v1/products?onlyActive=true"),
                HttpMethod.GET,
                new HttpEntity<>(authorizedHeaders(accessToken)),
                new ParameterizedTypeReference<Map<String, Object>>() {}
        );

        assertThat(activeProductsResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> activeProductsPage = activeProductsResponse.getBody();
        assertThat(activeProductsPage).isNotNull();
    }

    @Test
    @SuppressWarnings("null")
    void shouldSearchProductsSuccessfully() {
        String accessToken = authenticateTestUser();

        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
        UUID brandId = createTestBrand(accessToken, "Search Brand " + uniqueSuffix);
        UUID categoryId = createTestCategory(accessToken, "Search Category " + uniqueSuffix);

        String searchableProductName = "Searchable Product " + uniqueSuffix;

        CreateProductRequest createRequest = new CreateProductRequest(
                searchableProductName,
                "Description for search",
                brandId,
                categoryId,
                5000L,
                LocalDate.now().plusMonths(3)
        );

        HttpEntity<CreateProductRequest> createEntity = new HttpEntity<>(createRequest, authorizedJsonHeaders(accessToken));
        ResponseEntity<ProductResponse> createResponse = restTemplate.postForEntity(
                url("/api/v1/products"),
                createEntity,
                ProductResponse.class
        );

        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Search for the product
        ResponseEntity<Map<String, Object>> searchResponse = restTemplate.exchange(
                url("/api/v1/products/search?q=" + uniqueSuffix),
                HttpMethod.GET,
                new HttpEntity<>(authorizedHeaders(accessToken)),
                new ParameterizedTypeReference<Map<String, Object>>() {}
        );

        assertThat(searchResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> searchResults = searchResponse.getBody();
        assertThat(searchResults).isNotNull();
        assertThat(searchResults.get("totalElements")).isNotNull();
    }

    @Test
    @SuppressWarnings("null")
    void shouldGetProductsByBrandSuccessfully() {
        String accessToken = authenticateTestUser();

        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
        UUID brandId = createTestBrand(accessToken, "Brand Products " + uniqueSuffix);
        UUID categoryId = createTestCategory(accessToken, "Category " + uniqueSuffix);

        CreateProductRequest product1Request = new CreateProductRequest(
                "Brand Product 1 " + uniqueSuffix,
                "Product 1 description",
                brandId,
                categoryId,
                6000L,
                LocalDate.now().plusMonths(4)
        );

        CreateProductRequest product2Request = new CreateProductRequest(
                "Brand Product 2 " + uniqueSuffix,
                "Product 2 description",
                brandId,
                categoryId,
                7000L,
                LocalDate.now().plusMonths(5)
        );

        HttpEntity<CreateProductRequest> entity1 = new HttpEntity<>(product1Request, authorizedJsonHeaders(accessToken));
        HttpEntity<CreateProductRequest> entity2 = new HttpEntity<>(product2Request, authorizedJsonHeaders(accessToken));

        restTemplate.postForEntity(url("/api/v1/products"), entity1, ProductResponse.class);
        restTemplate.postForEntity(url("/api/v1/products"), entity2, ProductResponse.class);

        // Get products by brand
        ResponseEntity<Map<String, Object>> brandProductsResponse = restTemplate.exchange(
                url("/api/v1/products/brand/" + brandId),
                HttpMethod.GET,
                new HttpEntity<>(authorizedHeaders(accessToken)),
                new ParameterizedTypeReference<Map<String, Object>>() {}
        );

        assertThat(brandProductsResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> brandProducts = brandProductsResponse.getBody();
        assertThat(brandProducts).isNotNull();
        assertThat(brandProducts.get("totalElements")).isNotNull();
        Integer totalElements = (Integer) brandProducts.get("totalElements");
        assertThat(totalElements).isGreaterThanOrEqualTo(2);
    }

    @Test
    @SuppressWarnings("null")
    void shouldGetProductsByCategorySuccessfully() {
        String accessToken = authenticateTestUser();

        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
        UUID brandId = createTestBrand(accessToken, "Brand " + uniqueSuffix);
        UUID categoryId = createTestCategory(accessToken, "Category Products " + uniqueSuffix);

        CreateProductRequest product1Request = new CreateProductRequest(
                "Category Product 1 " + uniqueSuffix,
                "Product 1 description",
                brandId,
                categoryId,
                8000L,
                LocalDate.now().plusMonths(6)
        );

        CreateProductRequest product2Request = new CreateProductRequest(
                "Category Product 2 " + uniqueSuffix,
                "Product 2 description",
                brandId,
                categoryId,
                9000L,
                LocalDate.now().plusMonths(7)
        );

        HttpEntity<CreateProductRequest> entity1 = new HttpEntity<>(product1Request, authorizedJsonHeaders(accessToken));
        HttpEntity<CreateProductRequest> entity2 = new HttpEntity<>(product2Request, authorizedJsonHeaders(accessToken));

        restTemplate.postForEntity(url("/api/v1/products"), entity1, ProductResponse.class);
        restTemplate.postForEntity(url("/api/v1/products"), entity2, ProductResponse.class);

        // Get products by category
        ResponseEntity<Map<String, Object>> categoryProductsResponse = restTemplate.exchange(
                url("/api/v1/products/category/" + categoryId),
                HttpMethod.GET,
                new HttpEntity<>(authorizedHeaders(accessToken)),
                new ParameterizedTypeReference<Map<String, Object>>() {}
        );

        assertThat(categoryProductsResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> categoryProducts = categoryProductsResponse.getBody();
        assertThat(categoryProducts).isNotNull();
        assertThat(categoryProducts.get("totalElements")).isNotNull();
        Integer totalElements = (Integer) categoryProducts.get("totalElements");
        assertThat(totalElements).isGreaterThanOrEqualTo(2);
    }

    @Test
    @SuppressWarnings("null")
    void shouldGetExpiredAndExpiringSoonProductsSuccessfully() {
        String accessToken = authenticateTestUser();

        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
        UUID brandId = createTestBrand(accessToken, "Expiry Brand " + uniqueSuffix);
        UUID categoryId = createTestCategory(accessToken, "Expiry Category " + uniqueSuffix);

        // Create product expiring soon (within 30 days)
        CreateProductRequest expiringSoonRequest = new CreateProductRequest(
                "Expiring Soon Product " + uniqueSuffix,
                "Expires in 15 days",
                brandId,
                categoryId,
                3000L,
                LocalDate.now().plusDays(15)
        );

        HttpEntity<CreateProductRequest> expiringSoonEntity = new HttpEntity<>(expiringSoonRequest, authorizedJsonHeaders(accessToken));
        restTemplate.postForEntity(url("/api/v1/products"), expiringSoonEntity, ProductResponse.class);

        // Get products expiring soon
        ResponseEntity<Map<String, Object>> expiringSoonResponse = restTemplate.exchange(
                url("/api/v1/products/expiring-soon?days=30"),
                HttpMethod.GET,
                new HttpEntity<>(authorizedHeaders(accessToken)),
                new ParameterizedTypeReference<Map<String, Object>>() {}
        );

        assertThat(expiringSoonResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> expiringSoonProducts = expiringSoonResponse.getBody();
        assertThat(expiringSoonProducts).isNotNull();
        assertThat(expiringSoonProducts.get("totalElements")).isNotNull();

        // Get expired products (should return empty or previously expired products)
        ResponseEntity<Map<String, Object>> expiredResponse = restTemplate.exchange(
                url("/api/v1/products/expired"),
                HttpMethod.GET,
                new HttpEntity<>(authorizedHeaders(accessToken)),
                new ParameterizedTypeReference<Map<String, Object>>() {}
        );

        assertThat(expiredResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> expiredProducts = expiredResponse.getBody();
        assertThat(expiredProducts).isNotNull();
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

    private ProductResponse getProductById(String accessToken, UUID productId) {
        ResponseEntity<ProductResponse> response = restTemplate.exchange(
                url("/api/v1/products/" + productId),
                HttpMethod.GET,
                new HttpEntity<>(authorizedHeaders(accessToken)),
                ProductResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        ProductResponse body = response.getBody();
        assertThat(body).isNotNull();
        return body;
    }

    private ProductResponse getProductByName(String accessToken, String name) {
        ResponseEntity<ProductResponse> response = restTemplate.exchange(
                url("/api/v1/products/name/" + name),
                HttpMethod.GET,
                new HttpEntity<>(authorizedHeaders(accessToken)),
                ProductResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        ProductResponse body = response.getBody();
        assertThat(body).isNotNull();
        return body;
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
