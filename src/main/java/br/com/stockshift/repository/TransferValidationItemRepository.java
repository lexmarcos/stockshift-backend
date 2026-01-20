package br.com.stockshift.repository;

import br.com.stockshift.model.entity.TransferValidationItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransferValidationItemRepository extends JpaRepository<TransferValidationItem, UUID> {

    List<TransferValidationItem> findByTransferValidationId(UUID transferValidationId);

    @Query("SELECT tvi FROM TransferValidationItem tvi " +
           "JOIN tvi.stockMovementItem smi " +
           "JOIN smi.product p " +
           "WHERE tvi.transferValidation.id = :validationId AND p.barcode = :barcode")
    Optional<TransferValidationItem> findByValidationIdAndProductBarcode(
            @Param("validationId") UUID validationId,
            @Param("barcode") String barcode);
}
