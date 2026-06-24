package br.com.stockshift.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "product_image_thumbnails")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@IdClass(ProductImageThumbnail.ProductImageThumbnailId.class)
public class ProductImageThumbnail {

    @Id
    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Id
    @Column(name = "size", nullable = false, length = 10)
    private String size;

    @Column(name = "storage_key", nullable = false, length = 500)
    private String storageKey;

    @Column(name = "public_url", nullable = false, length = 500)
    private String publicUrl;

    @Column(name = "width_px", nullable = false)
    private Integer widthPx;

    @Column(name = "height_px")
    private Integer heightPx;

    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProductImageThumbnailId implements Serializable {
        private UUID productId;
        private String size;
    }
}
