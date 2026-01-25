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

        // Inject dependencies manually
        openAiService = new OpenAiService(
                restClient,
                "test-api-key",
                "gpt-5-nano",
                brandRepository,
                categoryRepository
        );
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

        // Mock objects
        Brand mahogany = new Brand();
        mahogany.setName("Mahogany");
        
        Brand mahoganyWithId = new Brand();
        mahoganyWithId.setId(UUID.randomUUID());
        mahoganyWithId.setName("Mahogany");

        Category perfumes = new Category();
        perfumes.setName("Perfumes");
        
        Category perfumesWithId = new Category();
        perfumesWithId.setId(UUID.randomUUID());
        perfumesWithId.setName("Perfumes");

        // Mock repositories
        when(brandRepository.findByTenantIdAndDeletedAtIsNull(any())).thenReturn(List.of(mahogany));
        when(categoryRepository.findByTenantIdAndDeletedAtIsNull(any())).thenReturn(List.of(perfumes));

        // Mock DB match logic
        when(brandRepository.findByNameIgnoreCaseAndTenantId(eq("Mahogany"), any()))
            .thenReturn(Optional.of(mahoganyWithId));
            
        when(categoryRepository.findByNameIgnoreCaseAndTenantId(eq("Perfumes"), any()))
            .thenReturn(Optional.of(perfumesWithId));

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
        assertThat(response.getCategoryName()).isEqualTo("Perfumes");
        assertThat(response.getCategoryId()).isNotNull();
    }
}
