package br.com.stockshift.service;

import br.com.stockshift.config.StorageProperties;
import br.com.stockshift.exception.InvalidFileTypeException;
import br.com.stockshift.exception.StorageException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnBean(S3Client.class)
public class StorageService {

    private final S3Client s3Client;
    private final StorageProperties properties;

    private static final Set<String> PRODUCT_IMAGE_TYPES = Set.of(
        "image/png", "image/jpeg", "image/jpg", "image/webp"
    );
    private static final Set<String> COMPANY_LOGO_TYPES = Set.of(
        "image/svg+xml", "image/png", "image/jpeg", "image/jpg"
    );
    private static final long COMPANY_LOGO_MAX_SIZE = 2 * 1024 * 1024;
    private static final String PRODUCT_FOLDER = "products/";
    private static final String COMPANY_LOGO_FOLDER = "company-logos/";

    public String uploadImage(MultipartFile file) {
        validateFileType(file, PRODUCT_IMAGE_TYPES, "Only PNG, JPG, JPEG and WEBP images are allowed");
        return uploadFile(file, PRODUCT_FOLDER);
    }

    public String uploadCompanyLogo(MultipartFile file) {
        validateCompanyLogo(file);
        return uploadFile(file, COMPANY_LOGO_FOLDER);
    }

    private String uploadFile(MultipartFile file, String folder) {
        try {
            String fileName = generateUniqueFileName(file.getOriginalFilename());
            String key = folder + fileName;

            PutObjectRequest request = PutObjectRequest.builder()
                .bucket(properties.getBucketName())
                .key(key)
                .contentType(file.getContentType())
                .build();

            s3Client.putObject(request,
                RequestBody.fromInputStream(file.getInputStream(), file.getSize()));

            String imageUrl = properties.getPublicUrl() + "/" + key;
            log.info("Image uploaded successfully: {}", imageUrl);
            return imageUrl;

        } catch (IOException e) {
            log.error("Failed to read image file", e);
            throw new StorageException("Failed to read image file", e);
        } catch (S3Exception e) {
            log.error("Failed to upload image to storage", e);
            throw new StorageException("Failed to upload image to storage", e);
        }
    }

    public void deleteImage(String imageUrl) {
        if (imageUrl == null || !imageUrl.startsWith(properties.getPublicUrl())) {
            return;
        }

        try {
            String key = extractKeyFromUrl(imageUrl);
            DeleteObjectRequest request = DeleteObjectRequest.builder()
                .bucket(properties.getBucketName())
                .key(key)
                .build();

            s3Client.deleteObject(request);
            log.info("Image deleted successfully: {}", imageUrl);

        } catch (NoSuchKeyException e) {
            log.warn("Image not found in storage (already deleted?): {}", imageUrl);
        } catch (S3Exception e) {
            log.error("Failed to delete image from storage: {}", imageUrl, e);
            throw new StorageException("Failed to delete image from storage", e);
        }
    }

    private void validateCompanyLogo(MultipartFile file) {
        validateFileType(file, COMPANY_LOGO_TYPES, "Only SVG, PNG, JPG and JPEG logos are allowed");
        if (file.getSize() > COMPANY_LOGO_MAX_SIZE) {
            throw new InvalidFileTypeException(
                "Company logo is too large. Got: " + file.getSize() + " bytes, max: " + COMPANY_LOGO_MAX_SIZE
            );
        }
    }

    private void validateFileType(MultipartFile file, Set<String> allowedTypes, String message) {
        if (file == null || file.isEmpty()) {
            throw new InvalidFileTypeException("File cannot be empty");
        }

        String contentType = file.getContentType();
        if (!allowedTypes.contains(contentType)) {
            throw new InvalidFileTypeException(
                message + ". Got: " + contentType
            );
        }
    }

    private String generateUniqueFileName(String originalName) {
        String extension = getFileExtension(originalName);
        return UUID.randomUUID().toString() + extension;
    }

    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf("."));
    }

    private String extractKeyFromUrl(String imageUrl) {
        String publicUrl = properties.getPublicUrl();
        if (imageUrl.startsWith(publicUrl)) {
            return imageUrl.substring(publicUrl.length() + 1);
        }
        throw new IllegalArgumentException("Invalid image URL: " + imageUrl);
    }
}
