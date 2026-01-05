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

    private static final Set<String> ALLOWED_TYPES = Set.of(
        "image/png", "image/jpeg", "image/jpg", "image/webp"
    );
    private static final String FOLDER = "products/";

    public String uploadImage(MultipartFile file) {
        try {
            validateFileType(file);
            String fileName = generateUniqueFileName(file.getOriginalFilename());
            String key = FOLDER + fileName;

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

        } catch (S3Exception e) {
            log.error("Failed to delete image from storage: {}", imageUrl, e);
            throw new StorageException("Failed to delete image from storage", e);
        }
    }

    private void validateFileType(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidFileTypeException("File cannot be empty");
        }

        String contentType = file.getContentType();
        if (!ALLOWED_TYPES.contains(contentType)) {
            throw new InvalidFileTypeException(
                "Only PNG, JPG, JPEG and WEBP images are allowed. Got: " + contentType
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
