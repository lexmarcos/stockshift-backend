package br.com.stockshift.service;

import br.com.stockshift.dto.ai.ProductClassificationResponse;
import br.com.stockshift.model.entity.Brand;
import br.com.stockshift.model.entity.Category;
import br.com.stockshift.repository.BrandRepository;
import br.com.stockshift.repository.CategoryRepository;
import br.com.stockshift.security.TenantContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class OpenAiService {

    private final RestClient restClient;
    private final String apiKey;
    private final String model;
    private final BrandRepository brandRepository;
    private final CategoryRepository categoryRepository;
    private final ObjectMapper objectMapper;

    public OpenAiService(RestClient restClient,
                         @Value("${openai.api-key}") String apiKey,
                         @Value("${openai.model}") String model,
                         BrandRepository brandRepository,
                         CategoryRepository categoryRepository) {
        this.restClient = restClient;
        this.apiKey = apiKey;
        this.model = model;
        this.brandRepository = brandRepository;
        this.categoryRepository = categoryRepository;
        this.objectMapper = new ObjectMapper();
    }

    public ProductClassificationResponse analyzeImage(MultipartFile image) throws IOException {
        String base64Image = Base64.getEncoder().encodeToString(image.getBytes());
        UUID tenantId = TenantContext.getTenantId();

        List<String> brands = brandRepository.findByTenantIdAndDeletedAtIsNull(tenantId).stream()
                .map(Brand::getName)
                .toList();

        List<String> categories = categoryRepository.findByTenantIdAndDeletedAtIsNull(tenantId).stream()
                .map(Category::getName)
                .toList();

        String prompt = buildPrompt(brands, categories);
        Map<String, Object> requestBody = buildRequestBody(base64Image, prompt);

        String responseBody = restClient.post()
                .uri("/v1/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .body(requestBody)
                .retrieve()
                .body(String.class);

        return parseResponse(responseBody, tenantId);
    }

    private String buildPrompt(List<String> brands, List<String> categories) {
        return String.format("""
            Analyze this product image. Identify the product name, brand, category, and volume (if applicable).
            
            Known Brands: %s
            Known Categories: %s
            
            Return ONLY a JSON object with this structure (no markdown formatting):
            {
              "name": "Product Name",
              "brand": "Brand Name" (try to match known brands exactly if possible),
              "category": "Category Name" (try to match known categories exactly if possible),
              "volume": {
                "value": number,
                "unit": "ml/g/kg/l"
              }
            }
            """, String.join(", ", brands), String.join(", ", categories));
    }

    private Map<String, Object> buildRequestBody(String base64Image, String prompt) {
        return Map.of(
            "model", model,
            "messages", List.of(
                Map.of(
                    "role", "user",
                    "content", List.of(
                        Map.of("type", "text", "text", prompt),
                        Map.of("type", "image_url", "image_url", Map.of("url", "data:image/jpeg;base64," + base64Image))
                    )
                )
            ),
            "max_tokens", 500
        );
    }

    private ProductClassificationResponse parseResponse(String responseBody, UUID tenantId) throws JsonProcessingException {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode choice = root.path("choices").get(0);
        String content = choice.path("message").path("content").asText();

        // Clean up markdown code blocks if present (OpenAI sometimes adds them even if told not to)
        if (content.startsWith("```json")) {
            content = content.substring(7);
        }
        if (content.startsWith("```")) {
            content = content.substring(3);
        }
        if (content.endsWith("```")) {
            content = content.substring(0, content.length() - 3);
        }

        JsonNode result = objectMapper.readTree(content);

        ProductClassificationResponse.ProductClassificationResponseBuilder responseBuilder = ProductClassificationResponse.builder()
                .name(result.path("name").asText())
                .detectedBrand(result.path("brand").asText())
                .detectedCategory(result.path("category").asText());

        if (result.has("volume")) {
            JsonNode volume = result.path("volume");
            responseBuilder.volumeValue(volume.path("value").asDouble());
            responseBuilder.volumeUnit(volume.path("unit").asText());
        }

        // Map to DB entities
        String brandName = result.path("brand").asText();
        if (brandName != null && !brandName.isEmpty()) {
            Optional<Brand> brand = brandRepository.findByNameIgnoreCaseAndTenantId(brandName, tenantId);
            if (brand.isPresent()) {
                responseBuilder.brandId(brand.get().getId());
                responseBuilder.brandName(brand.get().getName());
            }
        }

        String categoryName = result.path("category").asText();
        if (categoryName != null && !categoryName.isEmpty()) {
            Optional<Category> category = categoryRepository.findByNameIgnoreCaseAndTenantId(categoryName, tenantId);
            if (category.isPresent()) {
                responseBuilder.categoryId(category.get().getId());
                responseBuilder.categoryName(category.get().getName());
            }
        }

        return responseBuilder.build();
    }
}
