package br.com.stockshift.repository;

import br.com.stockshift.model.entity.ProductImageUpload;
import br.com.stockshift.model.enums.ProductImageUploadStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductImageUploadRepository extends JpaRepository<ProductImageUpload, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT upload FROM ProductImageUpload upload WHERE upload.tenantId = :tenantId "
            + "AND upload.id = :id AND upload.uploadedByUserId = :userId")
    Optional<ProductImageUpload> findForCurrentUser(
            @Param("tenantId") UUID tenantId,
            @Param("id") UUID id,
            @Param("userId") UUID userId);

    List<ProductImageUpload> findByStatusAndExpiresAtBefore(
            ProductImageUploadStatus status,
            LocalDateTime expiresAt);
}
