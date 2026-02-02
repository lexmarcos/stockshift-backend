package br.com.stockshift.repository;

import br.com.stockshift.model.entity.TransferValidationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TransferValidationLogRepository extends JpaRepository<TransferValidationLog, UUID> {

    List<TransferValidationLog> findAllByTransferId(UUID transferId);

    List<TransferValidationLog> findAllByTransferItemId(UUID transferItemId);
}
