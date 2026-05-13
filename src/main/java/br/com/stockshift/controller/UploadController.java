package br.com.stockshift.controller;

import br.com.stockshift.dto.ApiResponse;
import br.com.stockshift.dto.upload.TemporaryProductImageUploadResponse;
import br.com.stockshift.service.upload.ProductImageUploadService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/uploads")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer Authentication")
public class UploadController {

    private final ProductImageUploadService productImageUploadService;

    @PostMapping(value = "/product-images/temp", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@permissionGuard.hasAny('stock_movements:create')")
    public ResponseEntity<ApiResponse<TemporaryProductImageUploadResponse>> uploadTemporaryProductImage(
            @RequestPart("image") MultipartFile image) {
        TemporaryProductImageUploadResponse response =
                productImageUploadService.uploadTemporaryProductImage(image);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Temporary product image uploaded successfully", response));
    }
}
