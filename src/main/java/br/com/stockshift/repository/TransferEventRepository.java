package br.com.stockshift.repository;

import br.com.stockshift.model.entity.TransferEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TransferEventRepository extends JpaRepository<TransferEvent, UUID> {
    List<TransferEvent> findByTenantIdAndTransferIdOrderByPerformedAtAsc(UUID tenantId, UUID transferId);
}
