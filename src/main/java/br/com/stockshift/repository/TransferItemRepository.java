package br.com.stockshift.repository;

import br.com.stockshift.model.entity.TransferItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransferItemRepository extends JpaRepository<TransferItem, UUID> {

    List<TransferItem> findAllByTransferId(UUID transferId);

    @Query("SELECT ti FROM TransferItem ti WHERE ti.transfer.id = :transferId AND ti.productBarcode = :barcode")
    Optional<TransferItem> findByTransferIdAndProductBarcode(@Param("transferId") UUID transferId, @Param("barcode") String barcode);
}
