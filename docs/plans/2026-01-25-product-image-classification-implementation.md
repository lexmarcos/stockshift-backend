# Product Image Classification Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task.

**Goal:** Implement endpoint to classify products from images using OpenAI's vision model, returning JSON with product details.

**Architecture:**
- New `OpenAiService` to handle communication with OpenAI API using `RestClient`.
- New DTO `ProductClassificationResponse` for structured output.
- New Endpoint `POST /api/products/analyze-image` in `ProductController`.
- Stateless interaction: Images are processed in-memory (Base64) and not stored.

**Tech Stack:** Spring Boot 3, Java 17, Jackson, RestClient, JUnit 5.

---

### Task 1: Create DTOs

**Files:**
- Create: `src/main/java/br/com/stockshift/dto/ai/ProductClassificationResponse.java`

**Step 1: Create the DTO class**

```java
package br.com.stockshift.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductClassificationResponse {
    private String name;

    // Brand info
    private UUID brandId;
    private String brandName;

    // Category info
    private UUID categoryId;
    private String categoryName;

    // Volume/Attributes
    private Double volumeValue;
    private String volumeUnit;

    // Fallback info if no match found in DB
    private String detectedCategory;
    private String detectedBrand;
}
```

**Step 2: Commit**

```bash
git add src/main/java/br/com/stockshift/dto/ai/ProductClassificationResponse.java
git commit -m "feat: add ProductClassificationResponse DTO"
```

---

### Task 2: Implement OpenAiService (TDD)

**Files:**
- Create: `src/main/java/br/com/stockshift/service/OpenAiService.java`
- Create: `src/test/java/br/com/stockshift/service/OpenAiServiceTest.java`

**Step 1: Create Test Class with MockWebServer**

We'll use MockWebServer to simulate OpenAI API.

```java
package br.com.stockshift.service;

import br.com.stockshift.dto.ai.ProductClassificationResponse;
import br.com.stockshift.model.entity.Brand;
import br.com.stockshift.model.entity.Category;
import br.com.stockshift.repository.BrandRepository;
import br.com.stockshift.repository.CategoryRepository;
import br.com.stockshift.security.TenantContext;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OpenAiServiceTest {

    private OpenAiService openAiService;
    private MockWebServer mockWebServer;

    @Mock
    private BrandRepository brandRepository;
    @Mock
    private CategoryRepository categoryRepository;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        RestClient restClient = RestClient.builder()
                .baseUrl(mockWebServer.url("/").toString())
                .build();

        // Inject dependencies manually or via constructor
        openAiService = new OpenAiService(
                restClient,
                "test-api-key",
                "gpt-5-nano",
                brandRepository,
                categoryRepository
        );

        // Mock Tenant Context if needed (or use static mock)
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    void shouldAnalyzeImageAndReturnClassification() throws IOException {
        // Arrange
        MockMultipartFile image = new MockMultipartFile("image", "test.jpg", "image/jpeg", "content".getBytes());
        UUID tenantId = UUID.randomUUID();

        // Mock repositories
        when(brandRepository.findByTenantIdAndDeletedAtIsNull(any())).thenReturn(List.of(
            Brand.builder().name("Mahogany").build()
        ));
        when(categoryRepository.findByTenantIdAndDeletedAtIsNull(any())).thenReturn(List.of(
            Category.builder().name("Perfumes").build()
        ));

        // Mock DB match logic
        when(brandRepository.findByNameIgnoreCaseAndTenantId(eq("Mahogany"), any()))
            .thenReturn(Optional.of(Brand.builder().id(UUID.randomUUID()).name("Mahogany").build()));

        // Mock OpenAI Response
        String jsonResponse = """
            {
              "choices": [
                {
                  "message": {
                    "content": "{\\"name\\": \\"Make Me Fever Gold\\", \\"brand\\": \\"Mahogany\\", \\"category\\": \\"Perfumes\\", \\"volume\\": {\\"value\\": 100, \\"unit\\": \\"ml\\"}}"
                  }
                }
              ]
            }
        """;
        mockWebServer.enqueue(new MockResponse()
                .setBody(jsonResponse)
                .addHeader("Content-Type", "application/json"));

        // Act
        ProductClassificationResponse response = openAiService.analyzeImage(image);

        // Assert
        assertThat(response.getName()).isEqualTo("Make Me Fever Gold");
        assertThat(response.getBrandName()).isEqualTo("Mahogany");
        assertThat(response.getBrandId()).isNotNull();
    }
}
```

**Step 2: Create OpenAiService Class**

```java
package br.com.stockshift.service;

import br.com.stockshift.dto.ai.ProductClassificationResponse;
import br.com.stockshift.exception.BusinessException;
import br.com.stockshift.repository.BrandRepository;
import br.com.stockshift.repository.CategoryRepository;
import br.com.stockshift.security.TenantContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OpenAiService {

    private final RestClient restClient;

    @Value("${openai.secret-key}")
    private final String apiKey;

    @Value("${openai.model:gpt-5-nano}")
    private final String model;

    private final BrandRepository brandRepository;
    private final CategoryRepository categoryRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Constructor for testing if needed or let Lombok handle it (beware @Value)
    // Ideally use @ConfigurationProperties or setter injection for testability,
    // but for this plan we'll stick to standard Spring DI.

    public ProductClassificationResponse analyzeImage(MultipartFile image) {
        // Implementation here:
        // 1. Convert image to Base64
        // 2. Fetch brands/categories
        // 3. Build payload
        // 4. Call API
        // 5. Parse and map
        return null; // For TDD start
    }
}
```

**Step 3: Implement Logic to Pass Test**

- Implement `analyzeImage` method fully.
- Handle payload construction (careful with JSON escaping).
- Handle parsing logic.

**Step 4: Run Tests**

`./gradlew test --tests br.com.stockshift.service.OpenAiServiceTest`

**Step 5: Commit**

```bash
git add src/main/java/br/com/stockshift/service/OpenAiService.java src/test/java/br/com/stockshift/service/OpenAiServiceTest.java
git commit -m "feat: implement OpenAiService with basic analysis logic"
```

---

### Task 3: Config and Repositories Update

**Files:**
- Modify: `src/main/java/br/com/stockshift/repository/BrandRepository.java` (add `findByNameIgnoreCaseAndTenantId`)
- Modify: `src/main/java/br/com/stockshift/repository/CategoryRepository.java` (add `findByNameIgnoreCaseAndTenantId`)
- Modify: `src/main/java/br/com/stockshift/config/OpenApiConfig.java` (if needed for RestClient bean, otherwise create `RestClientConfig`)

**Step 1: Update Repositories**

Add methods to find by name to match AI output with DB.

**Step 2: Configure RestClient Bean**

Create a `@Configuration` class to provide `RestClient.Builder` or the `RestClient` itself if not present.

**Step 3: Commit**

```bash
git add .
git commit -m "chore: add repository methods and RestClient config"
```

---

### Task 4: Implement Controller Endpoint

**Files:**
- Modify: `src/main/java/br/com/stockshift/controller/ProductController.java`
- Test: `src/test/java/br/com/stockshift/controller/ProductControllerIntegrationTest.java`

**Step 1: Add Endpoint to Controller**

```java
    @PostMapping(value = "/analyze-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyAuthority('PRODUCT_CREATE', 'ROLE_ADMIN')")
    @Operation(summary = "Analyze product image using AI")
    public ResponseEntity<ApiResponse<ProductClassificationResponse>> analyzeImage(
            @RequestPart("image") MultipartFile image) {
        // Call service
        return null;
    }
```

**Step 2: Create Integration Test**

Test that the endpoint is reachable, secure, and returns 200 OK with mocked service.

**Step 3: Implement Controller Logic**

Connect controller to service.

**Step 4: Run Tests**

`./gradlew test --tests br.com.stockshift.controller.ProductControllerIntegrationTest`

**Step 5: Commit**

```bash
git add src/main/java/br/com/stockshift/controller/ProductController.java
git commit -m "feat: add analyze-image endpoint"
```

---

### Task 5: Final Verification

**Step 1: Run All Tests**

`./gradlew test`

**Step 2: Verify Linting/Checkstyle**

Ensure no unused imports or warnings.

**Step 3: Commit**

```bash
git commit --allow-empty -m "chore: final verification for product image classification"
```
