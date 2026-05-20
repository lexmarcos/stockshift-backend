package br.com.stockshift.controller;

import br.com.stockshift.dto.ai.ProductClassificationResponse;
import br.com.stockshift.dto.product.CategoryRequest;
import br.com.stockshift.dto.product.CategoryResponse;
import br.com.stockshift.dto.product.ProductRequest;
import br.com.stockshift.dto.product.ProductResponse;
import br.com.stockshift.dto.productprompt.ProductPromptCompanyAssetsResponse;
import br.com.stockshift.dto.productprompt.ProductPromptRequest;
import br.com.stockshift.dto.productprompt.ProductPromptResponse;
import br.com.stockshift.dto.upload.TemporaryProductImageUploadResponse;
import br.com.stockshift.service.CategoryService;
import br.com.stockshift.service.OpenAiService;
import br.com.stockshift.service.ProductPromptService;
import br.com.stockshift.service.ProductService;
import br.com.stockshift.service.upload.ProductImageUploadService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CatalogControllersTest {

    @Mock
    private CategoryService categoryService;
    @Mock
    private ProductService productService;
    @Mock
    private OpenAiService openAiService;
    @Mock
    private ProductImageUploadService productImageUploadService;
    @Mock
    private ProductPromptService productPromptService;

    @Test
    void categoryControllerShouldWrapCrudResponses() {
        CategoryController controller = new CategoryController(categoryService);
        UUID id = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        CategoryResponse response = CategoryResponse.builder()
                .id(id)
                .name("Bebidas")
                .parentCategoryId(parentId)
                .build();
        when(categoryService.create(any())).thenReturn(response);
        when(categoryService.findAll()).thenReturn(List.of(response));
        when(categoryService.findById(id)).thenReturn(response);
        when(categoryService.findByParentId(parentId)).thenReturn(List.of(response));
        when(categoryService.update(eq(id), any())).thenReturn(response);

        assertThat(controller.create(categoryRequest(parentId)).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(controller.findAll().getBody().getData()).singleElement().extracting("name")
                .isEqualTo("Bebidas");
        assertThat(controller.findById(id).getBody().getData().getId()).isEqualTo(id);
        assertThat(controller.findByParentId(parentId).getBody().getData()).hasSize(1);
        assertThat(controller.update(id, categoryRequest(parentId)).getBody().getSuccess()).isTrue();
        assertThat(controller.delete(id).getBody().getSuccess()).isTrue();
        verify(categoryService).delete(id);
    }

    @Test
    void productControllerShouldWrapCrudSearchAndImageAnalysisResponses() throws IOException {
        ProductController controller = new ProductController(productService, openAiService);
        UUID id = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        ProductResponse response = ProductResponse.builder()
                .id(id)
                .name("Produto")
                .sku("SKU-1")
                .barcode("789")
                .build();
        MockMultipartFile image = new MockMultipartFile("image", "product.png", "image/png", new byte[]{1});
        when(productService.create(any(), eq(image))).thenReturn(response);
        when(productService.findAll()).thenReturn(List.of(response));
        when(productService.findById(id)).thenReturn(response);
        when(productService.findByCategory(categoryId)).thenReturn(List.of(response));
        when(productService.findActive(true)).thenReturn(List.of(response));
        when(productService.search("prod")).thenReturn(List.of(response));
        when(productService.findByBarcode("789")).thenReturn(response);
        when(productService.findBySku("SKU-1")).thenReturn(response);
        when(productService.update(eq(id), any(), eq(image))).thenReturn(response);
        when(openAiService.analyzeImage(image)).thenReturn(ProductClassificationResponse.builder()
                .name("Produto")
                .detectedBrand("Marca")
                .build());

        assertThat(controller.create(productRequest(), image).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(controller.findAll().getBody().getData()).hasSize(1);
        assertThat(controller.findById(id).getBody().getData().getName()).isEqualTo("Produto");
        assertThat(controller.findByCategory(categoryId).getBody().getData()).hasSize(1);
        assertThat(controller.findActive(true).getBody().getData()).hasSize(1);
        assertThat(controller.search("prod").getBody().getData()).hasSize(1);
        assertThat(controller.findByBarcode("789").getBody().getData().getSku()).isEqualTo("SKU-1");
        assertThat(controller.findBySku("SKU-1").getBody().getData().getBarcode()).isEqualTo("789");
        assertThat(controller.update(id, productRequest(), image).getBody().getSuccess()).isTrue();
        assertThat(controller.delete(id).getBody().getSuccess()).isTrue();
        assertThat(controller.analyzeImage(image).getBody().getData().getDetectedBrand()).isEqualTo("Marca");
        verify(productService).delete(id);
    }

    @Test
    void uploadControllerShouldWrapTemporaryProductImageResponse() {
        UploadController controller = new UploadController(productImageUploadService);
        UUID uploadId = UUID.randomUUID();
        MockMultipartFile image = new MockMultipartFile("image", "p.png", "image/png", new byte[]{1});
        TemporaryProductImageUploadResponse response = TemporaryProductImageUploadResponse.builder()
                .uploadId(uploadId)
                .fileName("p.png")
                .contentType("image/png")
                .sizeBytes(1L)
                .build();
        when(productImageUploadService.uploadTemporaryProductImage(image)).thenReturn(response);

        assertThat(controller.uploadTemporaryProductImage(image).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(controller.uploadTemporaryProductImage(image).getBody().getData().getUploadId())
                .isEqualTo(uploadId);
    }

    @Test
    void productPromptControllerShouldWrapCrudResponses() {
        ProductPromptController controller = new ProductPromptController(productPromptService);
        UUID id = UUID.randomUUID();
        ProductPromptRequest request = ProductPromptRequest.builder()
                .name("Oferta")
                .prompt("Prompt")
                .build();
        ProductPromptResponse response = ProductPromptResponse.builder()
                .id(id)
                .name("Oferta")
                .prompt("Prompt")
                .imageUrl("https://cdn.example.com/prompt.png")
                .build();
        MockMultipartFile image = new MockMultipartFile("image", "prompt.png", "image/png", new byte[]{1});
        when(productPromptService.create(request, image)).thenReturn(response);
        when(productPromptService.findAll()).thenReturn(List.of(response));
        when(productPromptService.findById(id)).thenReturn(response);
        when(productPromptService.update(id, request, image)).thenReturn(response);
        when(productPromptService.getCompanyAssets()).thenReturn(
                ProductPromptCompanyAssetsResponse.builder()
                        .logoUrl("https://cdn.example.com/logo.png")
                        .build());

        assertThat(controller.create(request, image).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(controller.findAll().getBody().getData()).singleElement().extracting("id").isEqualTo(id);
        assertThat(controller.getCompanyAssets().getBody().getData().getLogoUrl())
                .isEqualTo("https://cdn.example.com/logo.png");
        assertThat(controller.findById(id).getBody().getData().getName()).isEqualTo("Oferta");
        assertThat(controller.update(id, request, image).getBody().getSuccess()).isTrue();
        assertThat(controller.delete(id).getBody().getSuccess()).isTrue();
        verify(productPromptService).delete(id);
    }

    private CategoryRequest categoryRequest(UUID parentId) {
        return CategoryRequest.builder()
                .name("Bebidas")
                .description("Produtos líquidos")
                .parentCategoryId(parentId)
                .build();
    }

    private ProductRequest productRequest() {
        return ProductRequest.builder()
                .name("Produto")
                .description("Descrição")
                .sku("SKU-1")
                .barcode("789")
                .build();
    }
}
