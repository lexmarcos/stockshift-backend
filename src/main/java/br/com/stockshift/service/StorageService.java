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
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import br.com.stockshift.service.imaging.ThumbnailGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnBean(S3Client.class)
public class StorageService {

    private final S3Client s3Client;
    private final StorageProperties properties;

    @Autowired(required = false)
    @Nullable
    private ThumbnailGenerator thumbnailGenerator;

    private static final int[] THUMBNAIL_WIDTHS = {150, 400, 800};
    private static final String[] THUMBNAIL_SUFFIXES = {"_sm", "_md", "_lg"};
    private static final float[] THUMBNAIL_QUALITIES = {0.80f, 0.82f, 0.85f};

    private static final Set<String> PRODUCT_IMAGE_TYPES = Set.of(
        "image/png", "image/jpeg", "image/jpg", "image/webp"
    );
    private static final Set<String> COMPANY_LOGO_TYPES = Set.of(
        "image/svg+xml", "image/png", "image/jpeg", "image/jpg"
    );
    private static final long COMPANY_LOGO_MAX_SIZE = 2 * 1024 * 1024;
    private static final String PRODUCT_FOLDER = "products/";
    private static final String COMPANY_LOGO_FOLDER = "company-logos/";
    private static final String TEMP_PRODUCT_FOLDER = "temp/product-images/";

    public record StoredImageObject(String key, String publicUrl) {
    }

    public record Thumbnails(
        StoredImageObject original,
        StoredImageObject small,
        StoredImageObject medium,
        StoredImageObject large
    ) {}

    public String uploadImage(MultipartFile file) {
        validateFileType(file, PRODUCT_IMAGE_TYPES, "Only PNG, JPG, JPEG and WEBP images are allowed");
        return uploadFile(file, PRODUCT_FOLDER);
    }

    public StoredImageObject uploadTemporaryProductImage(
            MultipartFile file,
            UUID tenantId,
            UUID uploadId) {
        validateFileType(file, PRODUCT_IMAGE_TYPES, "Only PNG, JPG, JPEG and WEBP images are allowed");
        String folder = TEMP_PRODUCT_FOLDER + tenantId + "/";
        return uploadFileObject(file, folder, uploadId.toString());
    }

    public StoredImageObject copyTemporaryProductImageToProductImage(
            String temporaryKey,
            String originalName) {
        String destinationKey = PRODUCT_FOLDER + generateUniqueFileName(originalName);
        copyObject(temporaryKey, destinationKey);
        return new StoredImageObject(destinationKey, buildPublicUrl(destinationKey));
    }

    public String uploadCompanyLogo(MultipartFile file) {
        validateCompanyLogo(file);
        return uploadFile(file, COMPANY_LOGO_FOLDER);
    }

    public Thumbnails uploadProductImageWithThumbnails(MultipartFile file) {
        validateFileType(file, PRODUCT_IMAGE_TYPES,
            "Only PNG, JPG, JPEG and WEBP images are allowed");

        StoredImageObject original = uploadFileObject(file, PRODUCT_FOLDER, null);
        StoredImageObject small = null;
        StoredImageObject medium = null;
        StoredImageObject large = null;

        if (thumbnailGenerator != null) {
            small = generateAndUploadThumbnail(file, 0, original);
            medium = generateAndUploadThumbnail(file, 1, original);
            large = generateAndUploadThumbnail(file, 2, original);
        }

        return new Thumbnails(original, small, medium, large);
    }

    private StoredImageObject generateAndUploadThumbnail(
            MultipartFile file, int sizeIndex, StoredImageObject original) {
        try {
            ThumbnailGenerator.ThumbnailSpec spec = new ThumbnailGenerator.ThumbnailSpec(
                THUMBNAIL_WIDTHS[sizeIndex], THUMBNAIL_QUALITIES[sizeIndex]);

            String thumbnailKey = deriveThumbnailKey(original.key(), THUMBNAIL_SUFFIXES[sizeIndex]);

            ThumbnailGenerator.ThumbnailResult result;
            try (InputStream originalStream = file.getInputStream()) {
                result = thumbnailGenerator.generate(originalStream, file.getContentType(),
                    file.getOriginalFilename(), spec);
            }

            byte[] bytes;
            try (InputStream thumbnailStream = result.inputStream()) {
                bytes = thumbnailStream.readAllBytes();
            }

            PutObjectRequest request = PutObjectRequest.builder()
                .bucket(properties.getBucketName())
                .key(thumbnailKey)
                .contentType("image/jpeg")
                .contentLength((long) bytes.length)
                .build();

            s3Client.putObject(request, RequestBody.fromBytes(bytes));

            String publicUrl = buildPublicUrl(thumbnailKey);
            log.info("Thumbnail uploaded: {}", publicUrl);
            return new StoredImageObject(thumbnailKey, publicUrl);
        } catch (Exception e) {
            log.warn("Failed to generate thumbnail {} for {}: {}",
                THUMBNAIL_SUFFIXES[sizeIndex], file.getOriginalFilename(), e.getMessage());
            return null;
        }
    }

    private String deriveThumbnailKey(String originalKey, String suffix) {
        int dotIndex = originalKey.lastIndexOf('.');
        if (dotIndex > 0) {
            return originalKey.substring(0, dotIndex) + suffix + ".jpg";
        }
        return originalKey + suffix + ".jpg";
    }

    private String uploadFile(MultipartFile file, String folder) {
        return uploadFileObject(file, folder, null).publicUrl();
    }

    private StoredImageObject uploadFileObject(MultipartFile file, String folder, String fileNamePrefix) {
        try {
            String fileName = generateUniqueFileName(file.getOriginalFilename(), fileNamePrefix);
            String key = folder + fileName;

            PutObjectRequest request = PutObjectRequest.builder()
                .bucket(properties.getBucketName())
                .key(key)
                .contentType(file.getContentType())
                .build();

            s3Client.putObject(request,
                RequestBody.fromInputStream(file.getInputStream(), file.getSize()));

            String imageUrl = buildPublicUrl(key);
            log.info("Image uploaded successfully: {}", imageUrl);
            return new StoredImageObject(key, imageUrl);

        } catch (IOException e) {
            log.error("Failed to read image file", e);
            throw new StorageException("Failed to read image file", e);
        } catch (S3Exception e) {
            log.error("Failed to upload image to storage", e);
            throw new StorageException("Failed to upload image to storage", e);
        }
    }

    private void copyObject(String sourceKey, String destinationKey) {
        try {
            CopyObjectRequest request = CopyObjectRequest.builder()
                    .sourceBucket(properties.getBucketName())
                    .sourceKey(sourceKey)
                    .destinationBucket(properties.getBucketName())
                    .destinationKey(destinationKey)
                    .build();
            s3Client.copyObject(request);
        } catch (S3Exception e) {
            log.error("Failed to copy image from {} to {}", sourceKey, destinationKey, e);
            throw new StorageException("Failed to copy image in storage", e);
        }
    }

    public void deleteImage(String imageUrl) {
        if (imageUrl == null || !imageUrl.startsWith(properties.getPublicUrl())) {
            return;
        }

        deleteKey(extractKeyFromUrl(imageUrl), imageUrl);
    }

    public void deleteProductImages(String imageUrl, List<String> thumbnailKeys) {
        deleteImage(imageUrl);
        if (thumbnailKeys != null) {
            thumbnailKeys.forEach(this::deleteStorageKeyQuietly);
        }
    }

    public void deleteStorageKeyQuietly(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            return;
        }

        try {
            deleteKey(storageKey, storageKey);
        } catch (RuntimeException exception) {
            log.warn("Failed to delete storage object: {}", storageKey, exception);
        }
    }

    private void deleteKey(String storageKey, String logTarget) {
        try {
            DeleteObjectRequest request = DeleteObjectRequest.builder()
                .bucket(properties.getBucketName())
                .key(storageKey)
                .build();

            s3Client.deleteObject(request);
            log.info("Image deleted successfully: {}", logTarget);

        } catch (NoSuchKeyException e) {
            log.warn("Image not found in storage (already deleted?): {}", logTarget);
        } catch (S3Exception e) {
            log.error("Failed to delete image from storage: {}", logTarget, e);
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
        return generateUniqueFileName(originalName, null);
    }

    private String generateUniqueFileName(String originalName, String fileNamePrefix) {
        String extension = getFileExtension(originalName);
        String prefix = fileNamePrefix == null ? UUID.randomUUID().toString() : fileNamePrefix;
        return prefix + extension;
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

    private String buildPublicUrl(String key) {
        return properties.getPublicUrl() + "/" + key;
    }
}
