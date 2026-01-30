package br.com.stockshift.repository;

import br.com.stockshift.model.entity.TransferItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TransferItemRepository extends JpaRepository<TransferItem, UUID> {

    List<TransferItem> findByTransferId(UUID transferId);

    List<TransferItem> findByTransferIdAndTenantId(UUID transferId, UUID tenantId);
}
